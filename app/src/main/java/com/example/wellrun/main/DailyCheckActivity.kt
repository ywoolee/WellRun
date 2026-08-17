package com.example.wellrun.main

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R

class DailyCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.daily_check)

        findViewById<Button>(R.id.btn_save_daily_check).setOnClickListener {
            // RunningActivity 대신 MainPageActivity(캘린더)로 이동하도록 수정
            val intent = Intent(this, MainPageActivity::class.java)

            // 팁: 캘린더 메인으로 갈 때는 기존에 쌓인 임시 화면들을 지워주는 것이 좋습니다.
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP

            startActivity(intent)
            finish() // 현재 데일리 체크 화면 닫기
        }
    }
}