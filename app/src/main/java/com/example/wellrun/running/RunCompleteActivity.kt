package com.example.wellrun.running

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.wellrun.R

class RunCompleteActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.run_complete)

        // TODO: run_complete.xml의 하단 '건너뛰기' 버튼 ID로 변경
        findViewById<Button>(R.id.btn_skip).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
            finish()
        }
    }
}