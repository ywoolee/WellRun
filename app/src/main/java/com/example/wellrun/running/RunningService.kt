package com.example.wellrun.running

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.wellrun.R
import com.google.android.gms.location.*
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

class RunningService : Service(), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient
    private lateinit var sensorManager: SensorManager

    private var pressureSensor: Sensor? = null
    private var currentBaroAltitude: Float? = null
    private var lastBaroAltitude: Float? = null

    private var lastValidAltitude: Double = 0.0
    private var totalElevationGain: Double = 0.0 // ✨ 누적 획득 고도
    private var splitElevationGain: Double = 0.0

    private var isRunning = false
    private var isPaused = false
    private var runStartTimeMs = 0L
    private var accumulatedTimeMs = 0L
    private var totalDistanceMeters = 0f
    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var lastPaceUpdateMs = 0L

    private val routeList = mutableListOf<Map<String, Double>>()
    private val splitsList = mutableListOf<Split>()
    private var nextSplitKm = 1
    private var lastSplitTimeMs = 0L

    private var currentHr = 0
    private var currentCadence = 0 // ✨ 현재 케이던스
    private val cadenceList = mutableListOf<Int>() // ✨ 평균 케이던스 계산용 바구니
    private val splitCadenceList = mutableListOf<Int>()
    private var currentPaceString = "-'--\""

    companion object {
        const val CHANNEL_ID = "RunningServiceChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        const val UPDATE_UI_ACTION = "UPDATE_UI_ACTION"
        const val RUN_FINISHED_ACTION = "RUN_FINISHED_ACTION"
    }
    data class Split(val km: String, val pace: String, val hr: Int, val cadence: Int, val elevation: Int)

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!isRunning || isPaused) return
            currentBaroAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0])
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        createNotificationChannel()
        setupLocationCallback()

        dataClient.addListener(this)
        messageClient.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("워치 연결 대기 중..."))
            }
            ACTION_PAUSE -> pauseRunning()
            ACTION_RESUME -> resumeRunning()
            ACTION_STOP -> stopRunningAndSendResult()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "러닝 기록", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        // ✨ 1. 알림을 눌렀을 때 띄울 화면(RunningActivity) 설정
        val intent = Intent(this, RunningActivity::class.java).apply {
            // 이미 켜져 있는 러닝 화면을 그대로 불러오기 위한 설정
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // ✨ 2. 인텐트를 PendingIntent로 포장 (안드로이드 12 이상 필수 플래그 IMMUTABLE 추가)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WellRun 러닝")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent) // ✨ 3. 알림에 클릭 이벤트(PendingIntent) 달아주기!
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(timeStr: String, distStr: String) {
        val notification = createNotification("시간: $timeStr | 거리: ${distStr}km")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isRunning || isPaused) return
                for (location in locationResult.locations) {
                    var measuredAlt: Double? = currentBaroAltitude?.toDouble() ?: if (location.hasAltitude()) location.altitude else null
                    if (measuredAlt != null) lastValidAltitude = measuredAlt else measuredAlt = lastValidAltitude

                    routeList.add(mapOf("lat" to location.latitude, "lng" to location.longitude, "alt" to measuredAlt))

                    if (lastLocation != null) {
                        val distance2D = lastLocation!!.distanceTo(location).toDouble()
                        var altitudeChange = 0.0

                        if (lastBaroAltitude != null && currentBaroAltitude != null) {
                            altitudeChange = (currentBaroAltitude!! - lastBaroAltitude!!).toDouble()
                        } else if (lastLocation!!.hasAltitude() && location.hasAltitude()) {
                            altitudeChange = location.altitude - lastLocation!!.altitude
                        }

                        // ✨ 획득 고도: 오르막(고도가 높아진 경우)일 때만 누적합니다
                        if (altitudeChange > 0) {
                            totalElevationGain += altitudeChange
                            splitElevationGain += altitudeChange
                        }

                        totalDistanceMeters += sqrt(distance2D.pow(2) + altitudeChange.pow(2)).toFloat()
                    }
                    lastLocation = location
                    lastBaroAltitude = currentBaroAltitude

                    sendUIUpdate(lat = location.latitude, lng = location.longitude)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRunning(delayMs: Long) {
        isRunning = true
        isPaused = false
        accumulatedTimeMs = delayMs
        runStartTimeMs = SystemClock.elapsedRealtime()
        totalDistanceMeters = 0f
        lastLocation = null
        lastBaroAltitude = null
        currentBaroAltitude = null

        // ✨ 리스트 및 누적 데이터 초기화
        totalElevationGain = 0.0
        splitElevationGain = 0.0 // ✨ 추가
        routeList.clear()
        splitsList.clear()
        cadenceList.clear()
        splitCadenceList.clear() // ✨ 추가
        nextSplitKm = 1
        lastSplitTimeMs = 0L

        pressureSensor?.let { sensorManager.registerListener(pressureListener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(2000).setMinUpdateDistanceMeters(3.0f).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        startTimer()
    }

    private fun pauseRunning() {
        if (isRunning && !isPaused) {
            isPaused = true
            accumulatedTimeMs += SystemClock.elapsedRealtime() - runStartTimeMs
            lastLocation = null
            lastBaroAltitude = null
            sendMessageToWatch("/pause_run")
        }
    }

    private fun resumeRunning() {
        if (isRunning && isPaused) {
            isPaused = false
            runStartTimeMs = SystemClock.elapsedRealtime()
            sendMessageToWatch("/resume_run")
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                if (isRunning) updateRunningStats()
                delay(1000)
            }
        }
    }

    private fun updateRunningStats() {
        val currentSegmentMs = if (isRunning && !isPaused) SystemClock.elapsedRealtime() - runStartTimeMs else 0L
        val totalElapsedMs = accumulatedTimeMs + currentSegmentMs
        val elapsedSeconds = totalElapsedMs / 1000
        val distanceKm = totalDistanceMeters / 1000.0

        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
        val distStr = String.format(Locale.getDefault(), "%.2f", distanceKm)

        val currentMs = SystemClock.elapsedRealtime()
        if (distanceKm > 0.01 && (currentMs - lastPaceUpdateMs >= 3000)) {
            lastPaceUpdateMs = currentMs
            val paceSecondsPerKm = (elapsedSeconds / distanceKm).toInt()
            currentPaceString = String.format(Locale.getDefault(), "%d'%02d\"", paceSecondsPerKm / 60, paceSecondsPerKm % 60)
            sendPaceToWatch(currentPaceString)
            sendDistanceToWatch(distStr)
        }

        if (distanceKm >= nextSplitKm) {
            val splitSec = (totalElapsedMs - lastSplitTimeMs) / 1000
            val splitPaceStr = String.format(Locale.getDefault(), "%d'%02d\"", splitSec / 60, splitSec % 60)
            // ✨ 구간 평균 케이던스 계산
            val splitAvgCadence = if (splitCadenceList.isNotEmpty()) splitCadenceList.average().toInt() else 0

            // ✨ 새로워진 Split 상자에 5가지 데이터 모두 담기
            splitsList.add(Split(nextSplitKm.toString(), splitPaceStr, currentHr, splitAvgCadence, splitElevationGain.toInt()))
            nextSplitKm++
            lastSplitTimeMs = totalElapsedMs
            splitElevationGain = 0.0
            splitCadenceList.clear()
        }

        updateNotification(timeStr, distStr)
        sendUIUpdate()
    }

    private fun sendUIUpdate(lat: Double = 0.0, lng: Double = 0.0) {
        val totalElapsedMs = accumulatedTimeMs + (if (isRunning && !isPaused) SystemClock.elapsedRealtime() - runStartTimeMs else 0L)
        val distanceKm = totalDistanceMeters / 1000.0
        val s = totalElapsedMs / 1000
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

        val updateIntent = Intent(UPDATE_UI_ACTION).apply {
            setPackage(packageName)
            putExtra("time", timeStr)
            putExtra("distance", String.format(Locale.getDefault(), "%.2f", distanceKm))
            putExtra("pace", currentPaceString)
            putExtra("hr", currentHr)
            putExtra("lat", lat)
            putExtra("lng", lng)
        }
        sendBroadcast(updateIntent)
    }

    private fun stopRunningAndSendResult() {
        if (isRunning && !isPaused) accumulatedTimeMs += SystemClock.elapsedRealtime() - runStartTimeMs
        isRunning = false
        isPaused = false
        sendMessageToWatch("/stop_hr")
        sensorManager.unregisterListener(pressureListener)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()

        stopForeground(STOP_FOREGROUND_REMOVE)

        val distanceKm = totalDistanceMeters / 1000.0
        val remainingDist = distanceKm - (nextSplitKm - 1)
        if (remainingDist > 0.0) {
            val splitSec = (accumulatedTimeMs - lastSplitTimeMs) / 1000
            val paceSec = if (remainingDist > 0) (splitSec / remainingDist).toInt() else 0
            val paceStr = String.format(Locale.getDefault(), "%d'%02d\"", paceSec / 60, paceSec % 60)
// ✨ 자투리 구간 평균 케이던스 계산
            val splitAvgCadence = if (splitCadenceList.isNotEmpty()) splitCadenceList.average().toInt() else 0

            splitsList.add(Split(String.format(Locale.getDefault(), "%.2f", remainingDist), paceStr, currentHr, splitAvgCadence, splitElevationGain.toInt()))

        }

        // ✨ 평균 케이던스 계산
        val avgCadence = if (cadenceList.isNotEmpty()) cadenceList.average().toInt() else 0

        val resultIntent = Intent(RUN_FINISHED_ACTION).apply {
            setPackage(packageName)
            putExtra("distanceKm", distanceKm)
            putExtra("elapsedSeconds", (accumulatedTimeMs / 1000).toInt())
            putExtra("avgPace", currentPaceString)
            putExtra("avgHr", currentHr)
            putExtra("avgCadence", avgCadence) // ✨ 평균 케이던스 추가
            putExtra("totalElevation", totalElevationGain) // ✨ 누적 고도 추가
            putExtra("routeJson", Gson().toJson(routeList))
            putExtra("splitsJson", Gson().toJson(splitsList))
        }
        sendBroadcast(resultIntent)
        stopSelf()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/heart_rate") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

                // ✨ 심박수와 케이던스 데이터를 수신하여 저장
                currentHr = dataMap.getInt("bpm")
                currentCadence = dataMap.getInt("cadence")

                if (currentCadence > 0) {
                    cadenceList.add(currentCadence)
                    splitCadenceList.add(currentCadence)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sensor_ready") {
            val watchTime = String(messageEvent.data).toLongOrNull() ?: System.currentTimeMillis()
            var delayMs = System.currentTimeMillis() - watchTime
            if (delayMs < 0) delayMs = 0L
            startRunning(delayMs)

            sendBroadcast(Intent("WATCH_READY_ACTION"))
        }
    }

    private fun sendMessageToWatch(path: String) = serviceScope.launch {
        try { Wearable.getNodeClient(this@RunningService).connectedNodes.await().forEach { Wearable.getMessageClient(this@RunningService).sendMessage(it.id, path, byteArrayOf()).await() } } catch (e: Exception) {}
    }
    private fun sendPaceToWatch(pace: String) = serviceScope.launch {
        try { Wearable.getNodeClient(this@RunningService).connectedNodes.await().forEach { Wearable.getMessageClient(this@RunningService).sendMessage(it.id, "/update_pace", pace.toByteArray()).await() } } catch (e: Exception) {}
    }
    private fun sendDistanceToWatch(dist: String) = serviceScope.launch {
        try { Wearable.getNodeClient(this@RunningService).connectedNodes.await().forEach { Wearable.getMessageClient(this@RunningService).sendMessage(it.id, "/update_distance", dist.toByteArray()).await() } } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }
}