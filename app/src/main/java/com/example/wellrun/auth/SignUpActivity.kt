package com.example.wellrun.auth

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.wellrun.R
import com.example.wellrun.model.User
import com.example.wellrun.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up)

        val etId = findViewById<EditText>(R.id.et_signup_id)
        val etPassword = findViewById<EditText>(R.id.et_signup_password)
        val etName = findViewById<EditText>(R.id.et_signup_name) // 닉네임 대신 이름으로 변경
        val btnNext = findViewById<AppCompatButton>(R.id.btn_next_to_step2) // 버튼 ID 변경

        btnNext.setOnClickListener {
            val id = etId.text.toString().trim()
            val pw = etPassword.text.toString().trim()
            val name = etName.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✨ 통신하지 않고 데이터를 Intent에 담아서 2단계 화면으로 이동!
            val intent = Intent(this, BasicInfoActivity::class.java).apply {
                putExtra("email", id)
                putExtra("password", pw)
                putExtra("nickname", name)
            }
            startActivity(intent)
        }
    }
}