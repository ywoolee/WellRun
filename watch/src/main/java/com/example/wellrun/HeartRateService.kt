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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager

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

    // 누적 만보기용 변수
    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var lastTotalSteps = -1f
    private var lastStepTimestamp = 0L

    // 워치 수면 방지를 위한 WakeLock 변수
    private var wakeLock: PowerManager.WakeLock? = null

    private val pauseResumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.wellrun.PAUSE_RUN" -> isPaused = true
                "com.example.wellrun.RESUME_RUN" -> isPaused = false
            }
        }
    }

    // 누적 걸음수(TYPE_STEP_COUNTER) 방식을 적용한 리스너
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val currentTotalSteps = event.values[0]
            val now = SystemClock.elapsedRealtime()

            if (lastTotalSteps < 0) {
                lastTotalSteps = currentTotalSteps
                lastStepTimestamp = now
                return
            }

            val deltaSteps = currentTotalSteps - lastTotalSteps
            val elapsedMs = now - lastStepTimestamp

            // ✨ 정확히 5초(5000ms) 이상 간격이 벌어졌을 때만 케이던스를 계산하여 데이터 튐 완벽 방어
            if (elapsedMs >= 5000L) {
                val cadence = (deltaSteps * 60000L / elapsedMs).toInt()

                if (cadence in 40..300) {
                    synchronized(cadenceListForMobile) { cadenceListForMobile.add(cadence) }
                    synchronized(cadenceListForUi) { cadenceListForUi.add(cadence) }
                }

                // 다음 구간 계산을 위해 현재 상태 업데이트
                lastTotalSteps = currentTotalSteps
                lastStepTimestamp = now
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startCadenceSensor() {
        val sm = getSystemService(SensorManager::class.java)
        if (sm == null) return
        sensorManager = sm

        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) return
        stepCounterSensor = sensor
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopCadenceSensor() {
        sensorManager?.unregisterListener(stepListener)
        lastTotalSteps = -1f
    }

    private fun startCadenceUiTimer() {
        serviceScope.launch {
            while (isActive) {
                // ✨ UI 갱신 주기도 폰 전송 주기와 완벽히 동일하게 5초(5000ms)로 통일
                delay(5000)
                var currentCadence = 0
                synchronized(cadenceListForUi) {
                    if (cadenceListForUi.isNotEmpty()) {
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

        // ✨ 폰으로 데이터를 보내는 주기도 기본 5초(5000ms)로 동작 중
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
                        latestBpm = bpmList.last()
                        bpmList.clear()
                    }
                }

                synchronized(cadenceListForMobile) {
                    if (cadenceListForMobile.isNotEmpty()) {
                        latestCadence = cadenceListForMobile.last()
                        cadenceListForMobile.clear()
                    }
                }

                if (latestBpm > 0 || latestCadence > 0) {
                    if (!isPaused) {
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