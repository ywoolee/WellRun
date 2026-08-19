package com.example.wellrun

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.ambient.AmbientLifecycleObserver

class MainActivity : ComponentActivity() {

    private lateinit var layoutLoading: LinearLayout
    private lateinit var layoutMain: LinearLayout

    // ✨ Chronometer 대신 거리(Distance) TextView로 변경
    private lateinit var textDistance: TextView
    private lateinit var textPace: TextView
    private lateinit var textBpm: TextView
    private lateinit var textCadence: TextView

    // ✨ 거리 수신기 추가
    private val distanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val distance = intent?.getStringExtra("distance") ?: DEFAULT_DISTANCE
            textDistance.text = distance
        }
    }

    // 페이스 수신기 (추가 필요 시 사용, 기존에 만들어두셨다면 유지)
    private val paceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pace = intent?.getStringExtra("pace") ?: DEFAULT_PACE
            textPace.text = pace
        }
    }

    private val bpmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val bpm = intent?.getIntExtra("bpm", 0) ?: 0
            if (bpm > 0) textBpm.text = bpm.toString()
        }
    }

    private val cadenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val cadence = intent?.getIntExtra("cadence", 0) ?: 0
            if (cadence > 0) textCadence.text = cadence.toString()
        }
    }

    private val acquiringReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            layoutMain.visibility = View.GONE
            layoutLoading.visibility = View.VISIBLE
        }
    }

    private val readyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (layoutLoading.visibility == View.VISIBLE) {
                layoutLoading.visibility = View.GONE
                layoutMain.visibility = View.VISIBLE
                // 초시계 로직 완전 삭제
            }
        }
    }

    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            resetToDefaults()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bodySensorsGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false
        val activityRecognitionGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false

        if (bodySensorsGranted && activityRecognitionGranted) {
            Toast.makeText(this, "권한 확인 완료! 스마트폰에서 러닝을 시작해주세요.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "측정을 위해 모든 센서 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {}
        override fun onExitAmbient() {
            layoutMain.visibility = View.VISIBLE
        }
    }
    private val ambientObserver = AmbientLifecycleObserver(this, ambientCallback)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        lifecycle.addObserver(ambientObserver)

        layoutLoading = findViewById(R.id.layout_loading)
        layoutMain = findViewById(R.id.layout_main)
        textDistance = findViewById(R.id.text_distance) // ✨ 거리 연결
        textPace = findViewById(R.id.text_pace)
        textBpm = findViewById(R.id.text_bpm)
        textCadence = findViewById(R.id.text_cadence)

        checkPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.registerReceiver(bpmReceiver, IntentFilter("com.example.wellrun.BPM_UPDATE"))
        lbm.registerReceiver(cadenceReceiver, IntentFilter("com.example.wellrun.CADENCE_UPDATE"))
        lbm.registerReceiver(resetReceiver, IntentFilter("com.example.wellrun.SENSORS_STOPPED"))
        lbm.registerReceiver(readyReceiver, IntentFilter("com.example.wellrun.SENSOR_READY"))
        lbm.registerReceiver(acquiringReceiver, IntentFilter("com.example.wellrun.SENSOR_ACQUIRING"))

        // ✨ 거리 및 페이스 수신기 등록
        lbm.registerReceiver(distanceReceiver, IntentFilter("com.example.wellrun.DISTANCE_UPDATE"))
        lbm.registerReceiver(paceReceiver, IntentFilter("com.example.wellrun.PACE_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        val lbm = LocalBroadcastManager.getInstance(this)
        lbm.unregisterReceiver(bpmReceiver)
        lbm.unregisterReceiver(cadenceReceiver)
        lbm.unregisterReceiver(resetReceiver)
        lbm.unregisterReceiver(readyReceiver)
        lbm.unregisterReceiver(acquiringReceiver)
        lbm.unregisterReceiver(distanceReceiver)
        lbm.unregisterReceiver(paceReceiver)
    }

    private fun resetToDefaults() {
        layoutLoading.visibility = View.GONE
        layoutMain.visibility = View.VISIBLE

        textBpm.text = DEFAULT_BPM
        textCadence.text = DEFAULT_CADENCE
        textPace.text = DEFAULT_PACE
        textDistance.text = DEFAULT_DISTANCE // ✨ 거리 초기화
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 1. 신체 센서 권한
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }

        // 2. 활동 인식 권한
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // ✨ 3. 대망의 알림 권한! (Wear OS 4 / API 33 이상 필수)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 요청할 권한이 하나라도 있다면 팝업 띄우기
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    companion object {
        private const val DEFAULT_BPM = "--"
        private const val DEFAULT_CADENCE = "--"
        private const val DEFAULT_PACE = "-'--\""
        private const val DEFAULT_DISTANCE = "0.00" // ✨ 기본 거리 문자열
    }
}