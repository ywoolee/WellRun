package com.example.wellrun.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.wellrun.R
import com.example.wellrun.main.MainPageActivity
import com.example.wellrun.model.User
import com.example.wellrun.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        // ✨ 새로 바뀐 디자인의 ID들로 연결해 줍니다.
        val etId = findViewById<EditText>(R.id.et_id)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<AppCompatButton>(R.id.btn_login)
        val tvSignUp = findViewById<TextView>(R.id.tv_sign_up)
        val tvFindPassword = findViewById<TextView>(R.id.tv_find_password)
        val sharedPref = getSharedPreferences("WellRunPrefs", Context.MODE_PRIVATE)
        // 로그인 버튼 클릭 시
        btnLogin.setOnClickListener {
            val id = etId.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (id.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버 통신용 User 객체 생성
            val user = User(email = id, password = password, nickname = "")

            // 서버로 로그인 요청 (Retrofit 사용)
            RetrofitClient.api.login(user).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@LoginActivity, response.body() ?: "로그인 성공!", Toast.LENGTH_SHORT).show()

                        SessionManager.saveUserId(this@LoginActivity, id)

                        // 메인 페이지로 이동
                        val intent = Intent(this@LoginActivity, MainPageActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@LoginActivity, "서버 연결 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // 회원가입 화면으로 이동
        tvSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        // 비밀번호 찾기 (추후 기능 추가를 위해 임시 메시지만 띄움)
        tvFindPassword.setOnClickListener {
            Toast.makeText(this, "비밀번호 찾기 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
        }
    }
}