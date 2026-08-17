package com.example.wellrun.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.wellrun.R

class BasicInfoActivity : AppCompatActivity() {

    private var selectedGender: String = ""
    private var selectedGoal: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up_2)

        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val nickname = intent.getStringExtra("nickname") ?: ""

        val etHeight = findViewById<EditText>(R.id.et_height)
        val etWeight = findViewById<EditText>(R.id.et_weight)
        val etAge = findViewById<EditText>(R.id.et_age)

        val btnMale = findViewById<AppCompatButton>(R.id.btn_gender_male)
        val btnFemale = findViewById<AppCompatButton>(R.id.btn_gender_female)

        val btnGoalMarathon = findViewById<AppCompatButton>(R.id.btn_goal_marathon)
        val btnGoalDiet = findViewById<AppCompatButton>(R.id.btn_goal_diet)
        val btnGoalStamina = findViewById<AppCompatButton>(R.id.btn_goal_stamina)

        // 성별 버튼 클릭 이벤트
        btnMale.setOnClickListener {
            selectedGender = "남성"
            setButtonSelected(btnMale, listOf(btnFemale))
        }
        btnFemale.setOnClickListener {
            selectedGender = "여성"
            setButtonSelected(btnFemale, listOf(btnMale))
        }

        // 목표 버튼 클릭 이벤트
        btnGoalMarathon.setOnClickListener {
            selectedGoal = "마라톤"
            setButtonSelected(btnGoalMarathon, listOf(btnGoalDiet, btnGoalStamina))
        }
        btnGoalDiet.setOnClickListener {
            selectedGoal = "다이어트"
            setButtonSelected(btnGoalDiet, listOf(btnGoalMarathon, btnGoalStamina))
        }
        btnGoalStamina.setOnClickListener {
            selectedGoal = "체력증진"
            setButtonSelected(btnGoalStamina, listOf(btnGoalMarathon, btnGoalDiet))
        }

        findViewById<AppCompatButton>(R.id.btn_next_to_step3).setOnClickListener {
            val heightStr = etHeight.text.toString().trim()
            val weightStr = etWeight.text.toString().trim()
            val ageStr = etAge.text.toString().trim()

            if (heightStr.isEmpty() || weightStr.isEmpty() || ageStr.isEmpty() || selectedGender.isEmpty() || selectedGoal.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력하고 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, InjuryInfoActivity::class.java).apply {
                putExtra("email", email)
                putExtra("password", password)
                putExtra("nickname", nickname)
                putExtra("height", heightStr.toDouble())
                putExtra("weight", weightStr.toDouble())
                putExtra("age", ageStr.toInt())
                putExtra("gender", selectedGender)
                putExtra("goal", selectedGoal)
            }
            startActivity(intent)
        }
    }

    // ✨ 버튼 색상을 바꿔주는 도우미 함수
    private fun setButtonSelected(selectedBtn: AppCompatButton, unselectedBtns: List<AppCompatButton>) {
        // 선택된 버튼은 주황색 배경에 흰색 글씨
        selectedBtn.setBackgroundResource(R.drawable.bg_orange_gradient)
        selectedBtn.setTextColor(Color.WHITE)

        // 나머지는 원래 회색으로 원상복구
        unselectedBtns.forEach {
            it.setBackgroundResource(R.drawable.bg_toggle_unselected)
            it.setTextColor(Color.parseColor("#888888"))
        }
    }
}