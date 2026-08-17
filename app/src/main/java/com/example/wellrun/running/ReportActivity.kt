package com.example.wellrun.running

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.model.RunningRecord
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var realRoute: List<LatLng> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.report)

        // 메인으로 가기 버튼 이벤트
        findViewById<Button>(R.id.report_btn_go_main).setOnClickListener {
            val intent = Intent(this, MainPageActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // 1. 현재 날짜 세팅
        val todayStr = SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.tv_report_date).text = todayStr

        // 2. 전달받은 데이터 꺼내기
        val recordJsonString = intent.getStringExtra("RUN_RECORD")
        if (recordJsonString != null) {
            val record = Gson().fromJson(recordJsonString, RunningRecord::class.java)

            // 3. 통계 그리드 데이터 바인딩
            findViewById<TextView>(R.id.tv_report_distance).text = String.format("%.2f km", record.distance)
            findViewById<TextView>(R.id.tv_report_pace).text = "${record.averagePace}/km"
            findViewById<TextView>(R.id.tv_report_hr).text = "${record.averageHeartRate} bpm"

            // 시간 변환 (초 -> MM:SS 형식)
            val m = record.durationSeconds / 60
            val s = record.durationSeconds % 60
            findViewById<TextView>(R.id.tv_report_duration).text = String.format("%02d:%02d", m, s)

            // 4. 경로 데이터 파싱 (지도용)
            if (!record.routeJson.isNullOrEmpty()) {
                val listType = object : TypeToken<List<Map<String, Double>>>() {}.type
                val parsedRoute: List<Map<String, Double>> = Gson().fromJson(record.routeJson, listType)
                realRoute = parsedRoute.map { LatLng(it["lat"] ?: 0.0, it["lng"] ?: 0.0) }
            }

            // 5. 구간별 상세 기록 (Splits Table) 동적 생성
            if (!record.splitsJson.isNullOrEmpty()) {
                val splitType = object : TypeToken<List<Split>>() {}.type
                val splits: List<Split> = Gson().fromJson(record.splitsJson, splitType)

                val splitContainer = findViewById<LinearLayout>(R.id.ll_split_table_container)

                for (split in splits) {
                    val rowLayout = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 24, 0, 24) // 상하 패딩 (8dp 정도)
                    }

                    // 1) 구간 (km) 텍스트
                    val tvKm = createTableTextView("${split.km} km", true, "#FFFFFF")
                    // 2) 페이스 텍스트 (예: 3km 구간에서 가장 빠르면 주황색으로 강조 등 커스텀 가능)
                    val tvPace = createTableTextView(split.pace, false, "#FFFFFF")
                    // 3) 심박수 텍스트
                    val tvHr = createTableTextView("${split.hr} bpm", false, "#FFFFFF")

                    rowLayout.addView(tvKm)
                    rowLayout.addView(tvPace)
                    rowLayout.addView(tvHr)

                    splitContainer.addView(rowLayout)
                }
            }
        }

        // 구글맵 로딩
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_report_container) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    // 표에 들어갈 텍스트뷰를 일정하게 생성해 주는 헬퍼 함수
    private fun createTableTextView(textStr: String, isBold: Boolean, colorHex: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = textStr
            setTextColor(Color.parseColor(colorHex))
            textSize = 14f
            gravity = Gravity.CENTER
            if (isBold) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true

        if (realRoute.isEmpty()) return

        val polylineOptions = PolylineOptions()
            .addAll(realRoute)
            .width(15f)
            .color(Color.parseColor("#FF6B35"))
            .geodesic(true)
            .startCap(com.google.android.gms.maps.model.RoundCap())
            .endCap(com.google.android.gms.maps.model.RoundCap())

        mMap.addPolyline(polylineOptions)

        val boundsBuilder = LatLngBounds.Builder()
        for (point in realRoute) {
            boundsBuilder.include(point)
        }
        val bounds = boundsBuilder.build()
        val padding = 120

        mMap.setOnMapLoadedCallback {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }
}