package com.example.wellrun.course

import android.content.Intent
import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.mypage.MyPageActivity
import com.example.wellrun.network.CourseResponse
import com.example.wellrun.network.RetrofitClient
import com.example.wellrun.network.TmapRetrofitClient
import com.example.wellrun.network.TmapSearchResponse
import com.example.wellrun.running.RunningActivity
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CourseActivity : AppCompatActivity() {

    // ✨ SK Open API에서 발급받은 T map App Key
    private val TMAP_APP_KEY = "ScSWi3m0f15CS0H944Sps9o8nI0UmDeYlbUgt4Z5"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.course)

        // 네비게이션 탭 설정
        findViewById<TextView>(R.id.nav_calendar).setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
        }
        findViewById<TextView>(R.id.nav_running).setOnClickListener {
            startActivity(Intent(this, RunningActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
        }
        findViewById<TextView>(R.id.nav_mypage).setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
            finish()
        }

        val etDeparture = findViewById<AutoCompleteTextView>(R.id.et_departure)
        val etDestination = findViewById<AutoCompleteTextView>(R.id.et_destination)
        val generateBtn = findViewById<Button>(R.id.btn_generate_course)

        // ✨ 1. 경사도 버튼과 거리 입력창 View 가져오기
        val btnSlopeFlat = findViewById<LinearLayout>(R.id.btn_slope_flat)
        val btnSlopeHilly = findViewById<LinearLayout>(R.id.btn_slope_hilly)
        val etTargetDistance = findViewById<EditText>(R.id.et_target_distance)

        val adapter = TmapAutoCompleteAdapter(this, TMAP_APP_KEY)
        etDeparture.setAdapter(adapter)
        etDestination.setAdapter(adapter)

        // ✨ 2. 현재 선택된 경사도 상태를 저장할 변수 (기본값: 평지)
        var isFlatSelected = true

        // ✨ 3. '평지' 버튼 클릭 이벤트
        btnSlopeFlat.setOnClickListener {
            isFlatSelected = true
            btnSlopeFlat.setBackgroundResource(R.drawable.bg_toggle_selected)
            btnSlopeHilly.setBackgroundResource(R.drawable.bg_toggle_unselected)
        }

        // ✨ 4. '오르막 포함' 버튼 클릭 이벤트
        btnSlopeHilly.setOnClickListener {
            isFlatSelected = false
            btnSlopeFlat.setBackgroundResource(R.drawable.bg_toggle_unselected)
            btnSlopeHilly.setBackgroundResource(R.drawable.bg_toggle_selected)
        }

        generateBtn.setOnClickListener {
            val depText = etDeparture.text.toString().trim()
            val destText = etDestination.text.toString().trim()

            if (depText.isEmpty() || destText.isEmpty()) {
                Toast.makeText(this, "출발지와 목적지를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✨ 5. 입력된 목표 거리(km) 가져오기 (비어있으면 기본값 0.0)
            val distanceStr = etTargetDistance.text.toString().trim()
            val targetDistance = if (distanceStr.isNotEmpty()) distanceStr.toDoubleOrNull() ?: 0.0 else 0.0

            generateBtn.text = "좌표 검색 중..."
            generateBtn.isEnabled = false

            // 1. 출발지 좌표 검색 시작
            searchCoordinate(depText) { startLat, startLng ->
                if (startLat == null || startLng == null) {
                    resetButton("출발지 검색 실패. 정확한 지명을 입력하세요.")
                    return@searchCoordinate
                }

                // 2. 목적지 좌표 검색 시작
                searchCoordinate(destText) { endLat, endLng ->
                    if (endLat == null || endLng == null) {
                        resetButton("목적지 검색 실패. 정확한 지명을 입력하세요.")
                        return@searchCoordinate
                    }

                    // 3. 두 좌표를 모두 성공적으로 얻었으면 우리 서버(Spring Boot)로 코스 탐색 요청!
                    generateBtn.text = "코스 탐색 중..."
                    // ✨ 파라미터 2개 추가
                    fetchOptimalCourse(startLat, startLng, endLat, endLng, targetDistance, isFlatSelected)
                }
            }
        }
    }

    // T맵 API를 호출하도록 수정된 좌표 검색 헬퍼 함수
    private fun searchCoordinate(query: String, callback: (Double?, Double?) -> Unit) {
        TmapRetrofitClient.api.searchPlace(appKey = TMAP_APP_KEY, searchKeyword = query)
            .enqueue(object : Callback<TmapSearchResponse> {
                override fun onResponse(call: Call<TmapSearchResponse>, response: Response<TmapSearchResponse>) {
                    val poiList = response.body()?.searchPoiInfo?.pois?.poi
                    if (response.isSuccessful && !poiList.isNullOrEmpty()) {
                        // 검색 결과 중 가장 정확도가 높은 첫 번째 데이터 사용
                        val lat = poiList[0].frontLat.toDoubleOrNull()
                        val lng = poiList[0].frontLon.toDoubleOrNull()
                        callback(lat, lng)
                    } else {
                        callback(null, null)
                    }
                }

                override fun onFailure(call: Call<TmapSearchResponse>, t: Throwable) {
                    callback(null, null)
                }
            })
    }

    // ✨ 파라미터 2개(targetDistance, isFlat)가 추가된 코스 탐색 함수
    private fun fetchOptimalCourse(startLat: Double, startLng: Double, endLat: Double, endLng: Double, targetDistance: Double, isFlat: Boolean) {
        RetrofitClient.api.generateCourse(startLat, startLng, endLat, endLng, targetDistance, isFlat)
            .enqueue(object : Callback<CourseResponse> {
                override fun onResponse(call: Call<CourseResponse>, response: Response<CourseResponse>) {
                    resetButton("경로 생성")

                    if (response.isSuccessful && response.body()?.status == "SUCCESS") {
                        val pathNodes = response.body()?.path
                        if (!pathNodes.isNullOrEmpty()) {
                            val pathJson = Gson().toJson(pathNodes)
                            val intent = Intent(this@CourseActivity, RunningActivity::class.java)
                            intent.putExtra("COURSE_PATH", pathJson)
                            startActivity(intent)
                        }
                    } else {
                        Toast.makeText(this@CourseActivity, "코스 생성 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<CourseResponse>, t: Throwable) {
                    resetButton("경로 생성")
                    Toast.makeText(this@CourseActivity, "에러: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    private fun resetButton(text: String) {
        val generateBtn = findViewById<Button>(R.id.btn_generate_course)
        generateBtn.text = text
        generateBtn.isEnabled = true
        if (text.contains("실패")) {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }
}