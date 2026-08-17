package com.example.wellrun.running

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.model.RunningRecord
import com.example.wellrun.network.RetrofitClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.wearable.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import com.example.wellrun.auth.SessionManager

// ✨ 1km마다의 기록을 임시로 담아둘 데이터 클래스
data class Split(val km: String, val pace: String, val hr: Int)

class RunningActivity : AppCompatActivity(), OnMapReadyCallback, DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000

    private lateinit var dataClient: DataClient
    private lateinit var messageClient: MessageClient

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private var currentBaroAltitude: Float? = null
    private var lastBaroAltitude: Float? = null

    // ✨ 직전 고도값을 기억할 변수 추가 (센서 튐 방지용)
    private var lastValidAltitude: Double = 0.0
    var totalElevationGain: Float = 0f

    private lateinit var tvRunTime: TextView
    private lateinit var tvRunDistance: TextView
    private lateinit var tvRunPace: TextView
    private lateinit var tvRunHeartRate: TextView

    private lateinit var btnStartRun: ImageButton
    private lateinit var btnPauseRun: ImageButton
    private lateinit var layoutRunningControls: LinearLayout
    private lateinit var btnBack: ImageButton

    private var isRunning = false
    private var isPaused = false
    private var runStartTimeMs = 0L
    private var accumulatedTimeMs = 0L

    private var totalDistanceMeters = 0f
    private var lastLocation: Location? = null
    private var timerJob: Job? = null
    private var lastPaceUpdateMs = 0L

    // ✨ DB 저장을 위해 데이터를 모아둘 바구니 (List)
    private val routeList = mutableListOf<Map<String, Double>>() // GPS 궤적
    private val splitsList = mutableListOf<Split>()              // 1km 구간 기록
    private var nextSplitKm = 1                                  // 다음 기록할 구간 (1km, 2km...)
    private var lastSplitTimeMs = 0L                             // 이전 구간을 통과했을 때의 시간

    private val pressureListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!isRunning || isPaused) return
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            if (currentBaroAltitude != null) {
                val delta = altitude - currentBaroAltitude!!
                if (delta > 0.3f) totalElevationGain += delta
            }
            currentBaroAltitude = altitude
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.running)

        tvRunTime = findViewById(R.id.tv_run_time)
        tvRunDistance = findViewById(R.id.tv_run_distance)
        tvRunPace = findViewById(R.id.tv_run_pace)
        tvRunHeartRate = findViewById(R.id.tv_run_heart_rate)

        btnStartRun = findViewById(R.id.btn_start_run)
        btnPauseRun = findViewById(R.id.btn_pause_run)
        layoutRunningControls = findViewById(R.id.layout_running_controls)
        btnBack = findViewById(R.id.running_btn_back)

        dataClient = Wearable.getDataClient(this)
        messageClient = Wearable.getMessageClient(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationCallback()

        btnStartRun.setOnClickListener {
            if (!isRunning) {
                sendMessageToWatch("/start_hr")
                Toast.makeText(this, "워치 센서 연결 중...", Toast.LENGTH_SHORT).show()
                btnStartRun.visibility = View.GONE
                layoutRunningControls.visibility = View.VISIBLE
                btnBack.isEnabled = false
                btnBack.alpha = 0.3f
            }
        }

        btnPauseRun.setOnClickListener {
            if (isRunning && !isPaused) {
                isPaused = true
                accumulatedTimeMs += SystemClock.elapsedRealtime() - runStartTimeMs
                lastLocation = null
                lastBaroAltitude = null
                btnPauseRun.setImageResource(android.R.drawable.ic_media_play)
                sendMessageToWatch("/pause_run")
                Toast.makeText(this, "러닝 일시정지", Toast.LENGTH_SHORT).show()
            } else if (isRunning && isPaused) {
                isPaused = false
                runStartTimeMs = SystemClock.elapsedRealtime()
                btnPauseRun.setImageResource(android.R.drawable.ic_media_pause)
                sendMessageToWatch("/resume_run")
                Toast.makeText(this, "러닝 재개", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            stopRunning()
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
        }

        val btnStopRun = findViewById<ImageButton>(R.id.btn_stop_run)

        // ✨ 정지 버튼 길게 누르기 완료 시 호출되는 로직 (데이터 변환 및 DB 전송)
        val stopRunnable = Runnable {
            stopRunning()
            saveRunDataToServer() // DB 저장 실행
        }

        btnStopRun.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { view.postDelayed(stopRunnable, 2000); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(stopRunnable); true }
                else -> false
            }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isRunning || isPaused) return

                for (location in locationResult.locations) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))

                    // ✨ 1. 측정된 고도값 확인 (기압계 최우선, 없으면 GPS)
                    var measuredAlt: Double? = null
                    if (currentBaroAltitude != null) {
                        measuredAlt = currentBaroAltitude!!.toDouble()
                    } else if (location.hasAltitude()) {
                        measuredAlt = location.altitude
                    }

                    // ✨ 2. 고도 데이터 빈자리 채우기 (없으면 직전 데이터 덮어쓰기)
                    if (measuredAlt != null) {
                        lastValidAltitude = measuredAlt
                    } else {
                        measuredAlt = lastValidAltitude
                    }

                    // ✨ 3. 현재 좌표와 고도를 routeList 배열에 담기 (JSON 변환용)
                    routeList.add(mapOf(
                        "lat" to location.latitude,
                        "lng" to location.longitude,
                        "alt" to measuredAlt
                    ))

                    if (lastLocation != null) {
                        val distance2D = lastLocation!!.distanceTo(location).toDouble()
                        var altitudeChange = 0.0
                        if (pressureSensor != null && lastBaroAltitude != null && currentBaroAltitude != null) {
                            altitudeChange = (currentBaroAltitude!! - lastBaroAltitude!!).toDouble()
                        } else if (lastLocation!!.hasAltitude() && location.hasAltitude()) {
                            altitudeChange = location.altitude - lastLocation!!.altitude
                        }
                        val distance3D = sqrt(distance2D.pow(2) + altitudeChange.pow(2)).toFloat()
                        totalDistanceMeters += distance3D
                    }

                    lastLocation = location
                    lastBaroAltitude = currentBaroAltitude
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // ✨ 최적화된 GPS 요청 설정 (배터리 & 데이터 용량 절약)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000) // 기본 3초 주기
            .setMinUpdateIntervalMillis(2000) // 최소 2초 대기
            //.setMinUpdateDistanceMeters(3.0f) // 최소 3미터 이동 시에만 기록
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateRunningStats() {
        val currentSegmentMs = if (isRunning && !isPaused) SystemClock.elapsedRealtime() - runStartTimeMs else 0L
        val totalElapsedMs = accumulatedTimeMs + currentSegmentMs
        val elapsedSeconds = totalElapsedMs / 1000
        val distanceKm = totalDistanceMeters / 1000.0

        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        tvRunTime.text = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)

        val distanceStr = String.format(Locale.getDefault(), "%.2f", distanceKm)
        tvRunDistance.text = distanceStr


        // 페이스 갱신
        val currentMs = SystemClock.elapsedRealtime()
        if (distanceKm > 0.01 && (currentMs - lastPaceUpdateMs >= 3000)) {
            lastPaceUpdateMs = currentMs
            val paceSecondsPerKm = (elapsedSeconds / distanceKm).toInt()
            val paceM = paceSecondsPerKm / 60
            val paceS = paceSecondsPerKm % 60
            val paceString = String.format(Locale.getDefault(), "%d'%02d\"", paceM, paceS)

            tvRunPace.text = paceString
            sendPaceToWatch(paceString)
            sendDistanceToWatch(distanceStr)
        }

        // ✨ 1km 통과 시마다 Splits 배열에 기록 남기기
        if (distanceKm >= nextSplitKm) {
            val splitElapsedMs = totalElapsedMs - lastSplitTimeMs
            val splitSeconds = splitElapsedMs / 1000
            val splitPaceM = splitSeconds / 60
            val splitPaceS = splitSeconds % 60
            val splitPaceString = String.format(Locale.getDefault(), "%d'%02d\"", splitPaceM, splitPaceS)

            val currentHr = tvRunHeartRate.text.toString().toIntOrNull() ?: 0

            splitsList.add(Split(nextSplitKm.toString(), splitPaceString, currentHr))

            nextSplitKm++
            lastSplitTimeMs = totalElapsedMs
        }
    }

    // ✨ 서버로 데이터를 보내는 핵심 함수
    private fun saveRunDataToServer() {
        val distanceKm = totalDistanceMeters / 1000.0

        if (distanceKm <= 0.01) {
            Toast.makeText(this, "러닝 거리가 너무 짧아 기록되지 않았습니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
            return
        }

        // ✨ 수정 1: 이미 stopRunning()에서 정확한 최종 시간이 저장되었으므로 깔끔하게 가져옵니다.
        val totalElapsedSeconds = (accumulatedTimeMs / 1000).toInt()
        val avgHr = tvRunHeartRate.text.toString().toIntOrNull() ?: 0

        val lastCompletedKm = nextSplitKm - 1
        val remainingDistance = distanceKm - lastCompletedKm

        if (remainingDistance > 0.0) {
            // ✨ 수정 2: 골치 아픈 조건문(isRunning 등)을 싹 빼고 최종 누적 시간을 씁니다.
            val totalElapsedMs = accumulatedTimeMs

            // 마지막 구간의 경과 시간 계산
            val splitElapsedMs = totalElapsedMs - lastSplitTimeMs
            val splitSeconds = splitElapsedMs / 1000

            // 마지막 구간의 페이스 계산
            val paceSecondsPerKm = if (remainingDistance > 0) (splitSeconds / remainingDistance).toInt() else 0
            val splitPaceM = paceSecondsPerKm / 60
            val splitPaceS = paceSecondsPerKm % 60
            val splitPaceString = String.format(Locale.getDefault(), "%d'%02d\"", splitPaceM, splitPaceS)

            val fractionalLabel = String.format(Locale.getDefault(), "%.2f", remainingDistance)

            splitsList.add(Split(fractionalLabel, splitPaceString, avgHr))
        }

        val gson = Gson()
        // 1. 수집한 리스트들을 JSON 텍스트로 변환 (이제 자투리 기록도 포함됨!)
        val routeJsonString = gson.toJson(routeList)
        val splitsJsonString = gson.toJson(splitsList)
        val loggedInUserId = SessionManager.getUserId(this)

        // 2. 서버로 보낼 RunningRecord 객체 조립
        val record = RunningRecord(
            userId = loggedInUserId,
            distance = distanceKm,
            durationSeconds = totalElapsedSeconds,
            averagePace = tvRunPace.text.toString(),
            averageHeartRate = avgHr,
            splitsJson = splitsJsonString,
            routeJson = routeJsonString
        )

        // 3. Retrofit으로 서버에 전송
        RetrofitClient.api.saveRunningRecord(record).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@RunningActivity, "기록 저장 완료!", Toast.LENGTH_SHORT).show()

                    // ✨ 데이터 상자(record)를 통째로 Json 문자열로 압축합니다.
                    val recordJsonString = Gson().toJson(record)

                    val intent = Intent(this@RunningActivity, RunCompleteActivity::class.java)
                    // ✨ Intent에 압축한 데이터를 "RUN_RECORD"라는 이름표를 붙여서 실어 보냅니다.
                    intent.putExtra("RUN_RECORD", recordJsonString)

                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RunningActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@RunningActivity, "서버 연결 실패: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                if (isRunning) updateRunningStats()
                delay(1000)
            }
        }
    }

    private fun startRunning(delayMs: Long = 0L) {
        isRunning = true
        isPaused = false
        accumulatedTimeMs = delayMs
        runStartTimeMs = SystemClock.elapsedRealtime()
        lastLocation = null
        lastBaroAltitude = null
        currentBaroAltitude = null
        totalElevationGain = 0f

        routeList.clear()
        splitsList.clear()
        nextSplitKm = 1
        lastSplitTimeMs = 0L

        pressureSensor?.let { sensorManager.registerListener(pressureListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startLocationUpdates()
        startTimer()
    }

    private fun stopRunning() {
        if (isRunning && !isPaused) {
            accumulatedTimeMs += SystemClock.elapsedRealtime() - runStartTimeMs
        }
        isRunning = false
        isPaused = false
        sendMessageToWatch("/stop_hr")
        sensorManager.unregisterListener(pressureListener)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/sensor_ready") {
            Log.d("WellRun", "워치 센서 준비 완료 신호 수신!")
            val watchTimeStr = String(messageEvent.data)
            val watchTime = watchTimeStr.toLongOrNull() ?: System.currentTimeMillis()
            var delayMs = System.currentTimeMillis() - watchTime
            if (delayMs < 0) delayMs = 0L
            runOnUiThread {
                Toast.makeText(this, "러닝을 시작합니다!", Toast.LENGTH_SHORT).show()
                startRunning(delayMs)
            }
        }
    }

    private fun sendMessageToWatch(path: String) {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RunningActivity).connectedNodes.await()
                nodes.forEach { node -> Wearable.getMessageClient(this@RunningActivity).sendMessage(node.id, path, byteArrayOf()).await() }
            } catch (e: Exception) { Log.e("WellRun", "워치로 신호 전송 실패", e) }
        }
    }

    private fun sendPaceToWatch(pace: String) {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RunningActivity).connectedNodes.await()
                nodes.forEach { node -> Wearable.getMessageClient(this@RunningActivity).sendMessage(node.id, "/update_pace", pace.toByteArray()).await() }
            } catch (e: Exception) { Log.e("WellRun", "페이스 전송 실패", e) }
        }
    }

    private fun sendDistanceToWatch(distance: String) {
        lifecycleScope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@RunningActivity).connectedNodes.await()
                nodes.forEach { node -> Wearable.getMessageClient(this@RunningActivity).sendMessage(node.id, "/update_distance", distance.toByteArray()).await() }
            } catch (e: Exception) { Log.e("WellRun", "거리 전송 실패", e) }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/heart_rate") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val bpm = dataMap.getInt("bpm")
                runOnUiThread { if (bpm > 0) tvRunHeartRate.text = bpm.toString() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
        messageClient.addListener(this)
        if (isRunning) {
            startLocationUpdates()
            pressureSensor?.let { sensorManager.registerListener(pressureListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        }
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        if (isRunning) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            sensorManager.unregisterListener(pressureListener)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }
}