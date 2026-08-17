package com.example.wellrun.mypage

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R

class EditProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_profile)

        // TODO: edit_profile.xml의 상단 '뒤로가기' 버튼 ID로 변경
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // TODO: 하단 '저장하기' 버튼 ID로 변경
        findViewById<Button>(R.id.btn_save_bottom).setOnClickListener {
            finish() // 저장 시 창을 닫고 이전 마이페이지로 돌아감
        }
    }
}