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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.localbroadcastmanager.content.LocalBroadcastManager // ✨ 추가됨
import androidx.wear.ongoing.OngoingActivity // ✨ 추가됨: 워치 페이스 복귀 인디케이터
import androidx.wear.ongoing.Status // ✨ 추가됨
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.ArrayDeque
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class HeartRateService : Service() {
    private var isPaused = false // ✨ 일시정지 상태 플래그

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
            // ✨ 1. 폰/워치의 시스템 수신 시간이 아닌, 센서 고유의 하드웨어 감지 시간 사용
            val now = event.timestamp / 1_000_000L

            synchronized(stepTimestamps) {
                stepTimestamps.addLast(now)

                // 8초가 지난 오래된 데이터 비우기
                while (stepTimestamps.isNotEmpty() && now - stepTimestamps.peekFirst() > cadenceWindowMs) {
                    stepTimestamps.removeFirst()
                }

                if (stepTimestamps.size >= 2) {
                    val elapsedMs = now - stepTimestamps.peekFirst()

                    // ✨ 2. 데이터가 한 번에 뭉쳐서 들어와 시간차(elapsedMs)가 너무 짧은 경우 계산 보류
                    if (elapsedMs > 2000L) {
                        val cadence = (stepTimestamps.size * 60_000L / elapsedMs).toInt()

                        // ✨ 3. 상식적인 인간의 케이던스 범위(40 ~ 300 SPM) 내의 데이터만 수집
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
                var averageCadence = 0
                synchronized(cadenceListForUi) {
                    if (cadenceListForUi.isNotEmpty()) {
                        averageCadence = cadenceListForUi.average().toInt()
                        cadenceListForUi.clear()
                    }
                }
                if (averageCadence > 0) {
                    val intent = Intent("com.example.wellrun.CADENCE_UPDATE")
                    intent.putExtra("cadence", averageCadence)
                    // ✨ LocalBroadcastManager 적용
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
                    // ✨ LocalBroadcastManager 적용
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
                // ✨ LocalBroadcastManager 적용
                LocalBroadcastManager.getInstance(this@HeartRateService).sendBroadcast(intent)

                synchronized(bpmList) { bpmList.add(latestBpm) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
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
                var averageBpm = 0
                var averageCadence = 0

                synchronized(bpmList) {
                    if (bpmList.isNotEmpty()) {
                        averageBpm = bpmList.average().toInt()
                        bpmList.clear()
                    }
                }

                synchronized(cadenceListForMobile) {
                    if (cadenceListForMobile.isNotEmpty()) {
                        averageCadence = cadenceListForMobile.average().toInt()
                        cadenceListForMobile.clear()
                    }
                }

                if (averageBpm > 0 || averageCadence > 0) {
                    // ✨ 일시정지 상태가 아닐 때만 폰으로 데이터 전송! (핵심)
                    if (!isPaused) {
                        sendDataToMobile(averageBpm, averageCadence)
                    }
                    updateNotification("BPM: $averageBpm | 케이던스: $averageCadence")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val loadingIntent = Intent("com.example.wellrun.SENSOR_ACQUIRING")
        // ✨ LocalBroadcastManager 적용 완료
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

    // ✨ 워치 페이스 인디케이터를 탭하면 MainActivity로 복귀시키는 PendingIntent
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

        // ✨ Ongoing Activity: 절전 2단계 타임아웃으로 워치 페이스가 뜨더라도
        // 러닝 세션 인디케이터가 함께 표시되고, 탭하면 바로 이 앱으로 복귀합니다.
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
        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback)
        stopCadenceSensor()
        serviceJob.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pauseResumeReceiver)

        val resetIntent = Intent("com.example.wellrun.SENSORS_STOPPED")
        // ✨ LocalBroadcastManager 적용
        LocalBroadcastManager.getInstance(this).sendBroadcast(resetIntent)
    }
    private fun sendReadySignalToMobile() {
        serviceScope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(this@HeartRateService)
                val nodes = nodeClient.connectedNodes.await()
                val messageClient = Wearable.getMessageClient(this@HeartRateService)

                // ✨ 현재 워치의 정확한 절대 시간을 바이트로 변환합니다.
                val watchStartTime = System.currentTimeMillis().toString().toByteArray()

                for (node in nodes) {
                    // 신호와 함께 시간 데이터(watchStartTime)를 같이 전송합니다.
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