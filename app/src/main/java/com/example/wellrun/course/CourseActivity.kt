package com.example.wellrun.course

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.mypage.MyPageActivity
import com.example.wellrun.running.RunningActivity

class CourseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.course) // course.xml 연결

        // 1. 하단 네비게이션: 메인(캘린더/러닝) 탭 이동

        findViewById<TextView>(R.id.nav_calendar).setOnClickListener {
            val intent = Intent(this, MainPageActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // 스택에 쌓인 화면 정리하고 메인으로 이동
            startActivity(intent)
        }
        // TODO: course.xml의 하단 '러닝' 또는 '캘린더' 텍스트뷰 ID로 변경해주세요
        findViewById<TextView>(R.id.nav_running).setOnClickListener {
            val intent = Intent(this, RunningActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // 스택에 쌓인 화면 정리하고 메인으로 이동
            startActivity(intent)
        }

        // 2. 하단 네비게이션: 마이페이지 탭 이동
        // TODO: course.xml의 하단 '마이페이지' 텍스트뷰 ID로 변경해주세요
        findViewById<TextView>(R.id.nav_mypage).setOnClickListener {
            val intent = Intent(this, MyPageActivity::class.java)
            startActivity(intent)
            finish() // 탭 이동 시 현재 액티비티는 닫아주는 것이 메모리 관리에 좋습니다
        }
    }
}