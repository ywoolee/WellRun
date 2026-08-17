package com.example.wellrun.running

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wellrun.R
import com.example.wellrun.auth.SessionManager
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.model.RunningRecord
import com.example.wellrun.network.RetrofitClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class RunningActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var tvRunTime: TextView
    private lateinit var tvRunDistance: TextView
    private lateinit var tvRunPace: TextView
    private lateinit var tvRunHeartRate: TextView
    private lateinit var btnStartRun: ImageButton
    private lateinit var btnPauseRun: ImageButton
    private lateinit var layoutRunningControls: LinearLayout
    private lateinit var btnBack: ImageButton

    private var isPaused = false
    private val PERMISSION_REQUEST_CODE = 1000

    // ✨ 서비스에서 1초마다, 혹은 러닝 종료 시 던져주는 데이터를 받아먹는 입(Receiver)
// ✨ 서비스에서 데이터를 받아먹는 입(Receiver)
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RunningService.UPDATE_UI_ACTION -> {

                    // 심박수를 먼저 꺼냅니다. (기본값은 0)
                    val currentHr = intent.getIntExtra("hr", 0)
                    tvRunHeartRate.text = currentHr.toString()

                    // 🏃‍♂️💨 [유저 인사이트 반영!] 심박수 데이터가 들어오기 시작하면 UI 즉시 전환!
                    if (currentHr > 0 && btnStartRun.visibility == View.VISIBLE) {
                        Toast.makeText(this@RunningActivity, "워치 심박수 수신 완료! 러닝을 시작합니다.", Toast.LENGTH_SHORT).show()
                        btnStartRun.visibility = View.GONE
                        layoutRunningControls.visibility = View.VISIBLE
                        btnBack.isEnabled = false
                        btnBack.alpha = 0.3f
                    }

                    // 나머지 데이터 업데이트
                    tvRunTime.text = intent.getStringExtra("time") ?: "00:00:00"
                    tvRunDistance.text = intent.getStringExtra("distance") ?: "0.00"
                    tvRunPace.text = intent.getStringExtra("pace") ?: "-'--\""

                    // 지도 이동
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lng = intent.getDoubleExtra("lng", 0.0)
                    if (lat != 0.0 && lng != 0.0 && ::mMap.isInitialized) {
                        if (mMap.cameraPosition.zoom < 10f) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
                        } else {
                            mMap.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
                        }
                    }
                }
                RunningService.RUN_FINISHED_ACTION -> {
                    saveRunDataToServer(intent) // 최종 데이터 뭉치를 받아서 서버로 슛!
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.running)

        checkPermissions()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map_container) as SupportMapFragment
        mapFragment.getMapAsync(this)

        tvRunTime = findViewById(R.id.tv_run_time)
        tvRunDistance = findViewById(R.id.tv_run_distance)
        tvRunPace = findViewById(R.id.tv_run_pace)
        tvRunHeartRate = findViewById(R.id.tv_run_heart_rate)
        btnStartRun = findViewById(R.id.btn_start_run)
        btnPauseRun = findViewById(R.id.btn_pause_run)
        layoutRunningControls = findViewById(R.id.layout_running_controls)
        btnBack = findViewById(R.id.running_btn_back)

        btnStartRun.setOnClickListener {
            btnStartRun.isEnabled = false
            Toast.makeText(this, "워치 센서 연결 중...", Toast.LENGTH_SHORT).show()

            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Toast.makeText(this, "연결된 워치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    btnStartRun.isEnabled = true
                } else {
                    nodes.forEach { node -> Wearable.getMessageClient(this).sendMessage(node.id, "/start_hr", byteArrayOf()) }

                    val serviceIntent = Intent(this, RunningService::class.java).apply { action = RunningService.ACTION_START }

                    startForegroundService(serviceIntent)
                }
            }
        }

        btnPauseRun.setOnClickListener {
            val serviceIntent = Intent(this, RunningService::class.java)
            if (!isPaused) {
                isPaused = true
                btnPauseRun.setImageResource(android.R.drawable.ic_media_play)
                serviceIntent.action = RunningService.ACTION_PAUSE
            } else {
                isPaused = false
                btnPauseRun.setImageResource(android.R.drawable.ic_media_pause)
                serviceIntent.action = RunningService.ACTION_RESUME
            }
            startService(serviceIntent)
        }

        btnBack.setOnClickListener {
            val serviceIntent = Intent(this, RunningService::class.java).apply { action = RunningService.ACTION_STOP }
            startService(serviceIntent)
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
        }

        val btnStopRun = findViewById<ImageButton>(R.id.btn_stop_run)
        val stopRunnable = Runnable {
            val serviceIntent = Intent(this, RunningService::class.java).apply { action = RunningService.ACTION_STOP }
            startService(serviceIntent) // 서비스 종료 신호를 보내면, 서비스가 계산 후 RUN_FINISHED_ACTION을 날려줌
        }

        btnStopRun.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { view.postDelayed(stopRunnable, 2000); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(stopRunnable); true }
                else -> false
            }
        }
    }

    private fun saveRunDataToServer(intent: Intent) {
        val distanceKm = intent.getDoubleExtra("distanceKm", 0.0)
        if (distanceKm <= 0.01) {
            Toast.makeText(this, "러닝 거리가 너무 짧아 기록되지 않았습니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainPageActivity::class.java))
            finish()
            return
        }

        val record = RunningRecord(
            userId = SessionManager.getUserId(this),
            distance = distanceKm,
            durationSeconds = intent.getIntExtra("elapsedSeconds", 0),
            averagePace = intent.getStringExtra("avgPace") ?: "-'--\"",
            averageHeartRate = intent.getIntExtra("avgHr", 0),
            averageCadence = intent.getIntExtra("avgCadence", 0), // ✨ 추가
            totalElevation = intent.getDoubleExtra("totalElevation", 0.0), // ✨ 추가
            splitsJson = intent.getStringExtra("splitsJson") ?: "[]",
            routeJson = intent.getStringExtra("routeJson") ?: "[]"
        )

        RetrofitClient.api.saveRunningRecord(record).enqueue(object : Callback<String> {
            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@RunningActivity, "기록 저장 완료!", Toast.LENGTH_SHORT).show()
                    val intentReport = Intent(this@RunningActivity, RunCompleteActivity::class.java)
                    intentReport.putExtra("RUN_RECORD", Gson().toJson(record))
                    startActivity(intentReport)
                    finish()
                } else {
                    Toast.makeText(this@RunningActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<String>, t: Throwable) {
                Toast.makeText(this@RunningActivity, "서버 연결 실패", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            // ✨ 다이어트하다 날려먹은 코드 복구: 지도 켜자마자 내 위치로 카메라 휙!
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 16f))
                }
            }
        }
    }
    private fun checkPermissions() {
        // ✨ 노란색 SDK_INT 경고 싹 제거! (프로젝트 설정이 이미 최신이므로 조건문 필요 없음)
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE
        )
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(RunningService.UPDATE_UI_ACTION)
            addAction("WATCH_READY_ACTION")
            addAction(RunningService.RUN_FINISHED_ACTION)
        }
        // ✨ 안드로이드 14 보안 정책에 맞게 단일 코드로 깔끔하게 변경 (에러 해결!)
        registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        // 앱이 화면에서 사라져도 Receiver 연결만 끊을 뿐, 뒤에 있는 RunningService는 쌩쌩하게 돌아갑니다!
        unregisterReceiver(serviceReceiver)
    }
}