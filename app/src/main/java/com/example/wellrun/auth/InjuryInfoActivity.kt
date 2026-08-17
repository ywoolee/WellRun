package com.example.wellrun.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
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

class InjuryInfoActivity : AppCompatActivity() {

    // ✨ 중복 선택을 위해 여러 개를 담을 수 있는 Set(바구니)을 사용합니다.
    private val selectedInjuries = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_3)

        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val nickname = intent.getStringExtra("nickname") ?: ""
        val height = intent.getDoubleExtra("height", 0.0)
        val weight = intent.getDoubleExtra("weight", 0.0)
        val age = intent.getIntExtra("age", 0)
        val gender = intent.getStringExtra("gender") ?: ""
        val goal = intent.getStringExtra("goal") ?: ""

        val btnKnee = findViewById<AppCompatButton>(R.id.btn_injury_knee)
        val btnAnkle = findViewById<AppCompatButton>(R.id.btn_injury_ankle)
        val btnFoot = findViewById<AppCompatButton>(R.id.btn_injury_foot)
        val btnShin = findViewById<AppCompatButton>(R.id.btn_injury_shin)
        val btnBack = findViewById<AppCompatButton>(R.id.btn_injury_back)
        val btnOther = findViewById<AppCompatButton>(R.id.btn_injury_other)

        val etOther = findViewById<EditText>(R.id.et_injury_other)
        val btnComplete = findViewById<AppCompatButton>(R.id.btn_injury_complete)

        // ✨ 버튼을 누를 때마다 ON/OFF를 스위치처럼 껐다 켜주는 기능
        val toggleListener = { btn: AppCompatButton, injury: String ->
            if (selectedInjuries.contains(injury)) {
                // 이미 들어있으면 바구니에서 빼고 회색으로 변경
                selectedInjuries.remove(injury)
                btn.setBackgroundResource(R.drawable.bg_toggle_unselected)
                btn.setTextColor(Color.parseColor("#888888"))
            } else {
                // 없으면 바구니에 담고 주황색으로 변경
                selectedInjuries.add(injury)
                btn.setBackgroundResource(R.drawable.bg_orange_gradient)
                btn.setTextColor(Color.WHITE)
            }
            updateCompleteButton(btnComplete)
        }

        // 각 버튼에 토글 기능 달아주기
        btnKnee.setOnClickListener { toggleListener(btnKnee, "무릎") }
        btnAnkle.setOnClickListener { toggleListener(btnAnkle, "발목") }
        btnFoot.setOnClickListener { toggleListener(btnFoot, "발바닥") }
        btnShin.setOnClickListener { toggleListener(btnShin, "정강이") }
        btnBack.setOnClickListener { toggleListener(btnBack, "허리") }

        // '기타' 버튼 클릭 이벤트
        btnOther.setOnClickListener {
            toggleListener(btnOther, "기타")
            // 기타가 바구니에 담겨있으면 입력창 켜기, 아니면 끄기
            if (selectedInjuries.contains("기타")) {
                etOther.visibility = View.VISIBLE
            } else {
                etOther.visibility = View.GONE
                etOther.text.clear() // 닫힐 때 글씨 지워주기
            }
        }

        btnComplete.setOnClickListener {
            // 바구니에 있는 부상 리스트 가져오기
            val finalInjuriesList = selectedInjuries.toMutableList()

            // 기타가 포함되어 있다면 직접 입력한 텍스트로 교체
            if (finalInjuriesList.contains("기타")) {
                val otherText = etOther.text.toString().trim()
                if (otherText.isEmpty()) {
                    Toast.makeText(this, "기타 부상 부위를 직접 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                finalInjuriesList.remove("기타")
                finalInjuriesList.add(otherText)
            }

            // ✨ 리스트에 있는 부상들을 쉼표(,)로 예쁘게 합치기 (예: "무릎, 발목, 허리")
            val finalInjuryString = if (finalInjuriesList.isEmpty()) {
                "없음"
            } else {
                finalInjuriesList.joinToString(", ")
            }

            val finalUser = User(
                email = email,
                password = password,
                nickname = nickname,
                height = height,
                weight = weight,
                age = age,
                gender = gender,
                goal = goal,
                injury = finalInjuryString
            )

            RetrofitClient.api.signUp(finalUser).enqueue(object : Callback<String> {
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@InjuryInfoActivity, "회원가입 완료!", Toast.LENGTH_SHORT).show()
                        val loginIntent = Intent(this@InjuryInfoActivity, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(loginIntent)
                        finish()
                    } else {
                        Toast.makeText(this@InjuryInfoActivity, "저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<String>, t: Throwable) {
                    Toast.makeText(this@InjuryInfoActivity, "서버 연결 실패: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // ✨ 바구니에 부상이 하나라도 있으면 버튼 색과 글씨를 '가입 완료'로 바꿔줌
    private fun updateCompleteButton(btnComplete: AppCompatButton) {
        if (selectedInjuries.isEmpty()) {
            btnComplete.text = "부상 없음 (건너뛰기)"
            btnComplete.setBackgroundResource(R.drawable.bg_toggle_unselected)
        } else {
            btnComplete.text = "가입 완료"
            btnComplete.setBackgroundResource(R.drawable.bg_orange_gradient)
        }
    }
}