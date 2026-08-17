package com.example.wellrun.mypage

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.course.CourseActivity
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.running.RunningActivity

class MyPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.mypage)

        // TODO: mypage.xml의 '정보수정' 버튼(또는 레이아웃) ID로 변경
        findViewById<View>(R.id.btn_edit_profile).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<TextView>(R.id.mypage_nav_calendar).setOnClickListener {
            startActivity(Intent(this, MainPageActivity::class.java))
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        findViewById<TextView>(R.id.mypage_nav_running).setOnClickListener {
            startActivity(Intent(this, RunningActivity::class.java))
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        findViewById<TextView>(R.id.mypage_nav_course).setOnClickListener {
            startActivity(Intent(this, CourseActivity::class.java))
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // (필요 시 하단 네비게이션 메인/코스 이동 버튼도 MainActivity처럼 연결해주세요!)
    }
}