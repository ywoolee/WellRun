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
            // ✨ 1. RunningActivity에서 넘겨준 러닝 기록(바통)을 꺼냅니다.
            val recordData = intent.getStringExtra("RUN_RECORD")

            // ✨ 2. ReportActivity로 가는 택시(Intent)를 부릅니다.
            val intentReport = Intent(this, ReportActivity::class.java)

            // ✨ 3. 꺼내둔 기록을 택시에 싣습니다!
            intentReport.putExtra("RUN_RECORD", recordData)

            // ✨ 4. 짐을 모두 실었으니 출발!
            startActivity(intentReport)
            finish()
        }
    }
}
