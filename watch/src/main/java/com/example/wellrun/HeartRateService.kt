package com.example.wellrun

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.ArrayDeque
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager // ✨ WakeLock을 위한 PowerManager 임포트

class HeartRateService : Service() {
    private var isPaused = false

    private val channelId = "HeartRateChannel"
    private val notificationId = 1
    private val measureClient by lazy { HealthServices.getClient(this).measureClient }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val bpmList = mutableListOf<Int>()
    private val cadenceListForMobile = mutableListOf<Int>()
    private val cadenceListForUi = mutableListOf<Int>()

    private var sensorManager: SensorManager? = null
    private var stepDetectorSensor: Sensor? = null
    private val stepTimestamps = ArrayDeque<Long>()
    private val cadenceWindowMs = 8000L

    // ✨ 워치 수면 방지를 위한 WakeLock 변수
    private var wakeLock: PowerManager.WakeLock? = null

    private val pauseResumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.wellrun.PAUSE_RUN" -> isPaused = true
                "com.example.wellrun.RESUME_RUN" -> isPaused = false
            }
        }
    }

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = event.timestamp / 1_000_000L

            synchronized(stepTimestamps) {
                stepTimestamps.addLast(now)

                while (stepTimestamps.isNotEmpty() && now - stepTimestamps.peekFirst() > cadenceWindowMs) {
                    stepTimestamps.removeFirst()
                }

                if (stepTimestamps.size >= 2) {
                    val elapsedMs = now - stepTimestamps.peekFirst()

                    if (elapsedMs > 2000L) {
                        // ✨ 걸음 '간격'을 계산하기 위해 size - 1 로 수정
                        val cadence = ((stepTimestamps.size - 1) * 60_000L / elapsedMs).toInt()

                        if (cadence in 40..300) {
                            synchronized(cadenceListForUi) { cadenceListForUi.add(cadence) }
                            synchronized(cadenceListForMobile) { cadenceListForMobile.add(cadence) }
                        }
                    }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startCadenceSensor() {
        val sm = getSystemService(SensorManager::class.java)
        if (sm == null) return
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) return
        stepDetectorSensor = sensor
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopCadenceSensor() {
        sensorManager?.unregisterListener(stepListener)
        synchronized(stepTimestamps) { stepTimestamps.clear() }
    }

    private fun startCadenceUiTimer() {
        serviceScope.launch {
            while (isActive) {
                delay(3000)
                var currentCadence = 0
                synchronized(cadenceListForUi) {
                    if (cadenceListForUi.isNotEmpty()) {
                        // ✨ 화면 UI에는 평균 대신 가장 최근 측정된 값 표시
                        currentCadence = cadenceListForUi.last()
                        cadenceListForUi.clear()
                    }
                }
                if (currentCadence > 0) {
                    val intent = Intent("com.example.wellrun.CADENCE_UPDATE")
                    intent.putExtra("cadence", currentCadence)
                    LocalBroadcastManager.getInstance(this@HeartRateService).sendBroadcast(intent)
                }
            }
        }
    }

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability is DataTypeAvailability) {
                Log.d("WellRun", "센서 상태 변경($dataType): $availability")
                if (availability == DataTypeAvailability.AVAILABLE) {
                    val intent = Intent("com.example.wellrun.SENSOR_READY")
                    LocalBroadcastManager.getInstance(this@HeartRateService).sendBroadcast(intent)
                    sendReadySignalToMobile()
                }
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            val latestBpm = heartRateData.lastOrNull()?.value?.toInt()

            if (latestBpm != null && latestBpm > 0) {
                val intent = Intent("com.example.wellrun.BPM_UPDATE")
                intent.putExtra("bpm", latestBpm)
                LocalBroadcastManager.getInstance(this@HeartRateService).sendBroadcast(intent)

                synchronized(bpmList) { bpmList.add(latestBpm) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // ✨ WakeLock 획득: 화면이 꺼져도 CPU가 멈추지 않고 걸음 수를 계속 셉니다!
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WellRun:CadenceWakeLock")
        wakeLock?.acquire()

        createNotificationChannel()
        startForeground(notificationId, createNotification("센서 측정 준비 중..."))

        val filter = IntentFilter().apply {
            addAction("com.example.wellrun.PAUSE_RUN")
            addAction("com.example.wellrun.RESUME_RUN")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(pauseResumeReceiver, filter)

        startAverageTimer()
        startCadenceSensor()
        startCadenceUiTimer()
    }

    private fun startAverageTimer() {
        serviceScope.launch {
            while (isActive) {
                delay(5000)
                var latestBpm = 0
                var latestCadence = 0

                synchronized(bpmList) {
                    if (bpmList.isNotEmpty()) {
                        // ✨ 평균(average)이 아니라 가장 최신 데이터(last) 추출
                        latestBpm = bpmList.last()
                        bpmList.clear()
                    }
                }

                synchronized(cadenceListForMobile) {
                    if (cadenceListForMobile.isNotEmpty()) {
                        // ✨ 평균(average)이 아니라 가장 최신 데이터(last) 추출
                        latestCadence = cadenceListForMobile.last()
                        cadenceListForMobile.clear()
                    }
                }

                if (latestBpm > 0 || latestCadence > 0) {
                    if (!isPaused) {
                        // ✨ 이제 폰으로는 가장 싱싱한 최신값(latest)이 날아갑니다.
                        sendDataToMobile(latestBpm, latestCadence)
                    }
                    updateNotification("BPM: $latestBpm | 케이던스: $latestCadence")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val loadingIntent = Intent("com.example.wellrun.SENSOR_ACQUIRING")
        LocalBroadcastManager.getInstance(this).sendBroadcast(loadingIntent)

        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
        return START_STICKY
    }

    private fun sendDataToMobile(bpm: Int, cadence: Int) {
        val dataClient = Wearable.getDataClient(this)
        val putDataRequest = PutDataMapRequest.create("/heart_rate").apply {
            dataMap.putInt("bpm", bpm)
            dataMap.putInt("cadence", cadence)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(putDataRequest)
        Log.d("WellRun", "모바일로 전송 - BPM: $bpm, 케이던스: $cadence")
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            channelId,
            "Sensor Sync",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
    }

    private fun buildReturnToAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotification(text: String): Notification {
        val touchIntent = buildReturnToAppIntent()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WellRun - Sensors")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(touchIntent)

        val ongoingActivityStatus = Status.Builder()
            .addTemplate(text)
            .build()

        val ongoingActivity = OngoingActivity.Builder(
            applicationContext, notificationId, notificationBuilder
        )
            .setStaticIcon(android.R.drawable.ic_menu_mylocation)
            .setTouchIntent(touchIntent)
            .setStatus(ongoingActivityStatus)
            .build()

        ongoingActivity.apply(applicationContext)

        return notificationBuilder.build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(notificationId, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()

        // ✨ 서비스가 끝날 때 반드시 락을 풀어주어 배터리 소모를 막습니다.
        wakeLock?.release()

        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback)
        stopCadenceSensor()
        serviceJob.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseResumeReceiver)

        val resetIntent = Intent("com.example.wellrun.SENSORS_STOPPED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(resetIntent)
    }
    private fun sendReadySignalToMobile() {
        serviceScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(this@HeartRateService)
                val nodes = nodeClient.connectedNodes.await()
                val messageClient = Wearable.getMessageClient(this@HeartRateService)

                val watchStartTime = System.currentTimeMillis().toString().toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/sensor_ready", watchStartTime).await()
                }
                Log.d("WellRun", "스마트폰으로 /sensor_ready 신호 및 시간 전송 완료!")
            } catch (e: Exception) {
                Log.e("WellRun", "스마트폰으로 신호 전송 실패", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}