package com.example.wellrun.main

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.course.CourseActivity
import com.example.wellrun.mypage.MyPageActivity
import com.example.wellrun.R
import com.example.wellrun.running.RunningActivity

class MainPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main) // main.xml 연결
        //컨디션 체크 화면

        findViewById<Button>(R.id.main_daily_check).setOnClickListener {
            startActivity(Intent(this, DailyCheckActivity::class.java))
        }
        // 1. 러닝 시작 버튼 -> 데일리 체크 화면으로 이동
        // TODO: main.xml의 '러닝 시작' 버튼 ID로 변경하세요. (예: R.id.btn_start_run)
        findViewById<TextView>(R.id.main_nav_running).setOnClickListener {
            startActivity(Intent(this, RunningActivity::class.java))
        }

        // 2. 하단 네비게이션: 코스 탭 이동
        // TODO: main.xml의 하단 '코스' 텍스트뷰 ID로 변경하세요.
        findViewById<TextView>(R.id.main_nav_course).setOnClickListener {
            startActivity(Intent(this, CourseActivity::class.java))
        }

        // 3. 하단 네비게이션: 마이페이지 탭 이동
        // TODO: main.xml의 하단 '마이페이지' 텍스트뷰 ID로 변경하세요.
        findViewById<TextView>(R.id.main_nav_mypage).setOnClickListener {
            startActivity(Intent(this, MyPageActivity::class.java))
        }
    }
}