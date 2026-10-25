package com.example.wellrun.mypage

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.wellrun.R
import com.example.wellrun.auth.SessionManager
import com.example.wellrun.model.User
import com.example.wellrun.network.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var etAge: EditText

    private lateinit var btnGenderMale: AppCompatButton
    private lateinit var btnGenderFemale: AppCompatButton

    private lateinit var btnGoalMarathon: AppCompatButton
    private lateinit var btnGoalDiet: AppCompatButton
    private lateinit var btnGoalFitness: AppCompatButton

    private var selectedGender: String = "남성"
    private var selectedGoal: String = "마라톤"

    // 기존 비밀번호나 부상 정보를 유지하기 위해 원본 유저 객체 보관
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.edit_profile)

        initViews()
        setupToggleListeners()
        loadCurrentUserProfile()

        // 상단 뒤로가기 버튼
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // 하단 저장하기 버튼
        findViewById<AppCompatButton>(R.id.btn_save_bottom).setOnClickListener {
            saveUserProfile()
        }
    }

    private fun initViews() {
        etName = findViewById(R.id.et_name)
        etHeight = findViewById(R.id.et_height)
        etWeight = findViewById(R.id.et_weight)
        etAge = findViewById(R.id.et_age)

        btnGenderMale = findViewById(R.id.btn_gender_male)
        btnGenderFemale = findViewById(R.id.btn_gender_female)

        btnGoalMarathon = findViewById(R.id.btn_goal_marathon)
        btnGoalDiet = findViewById(R.id.btn_goal_diet)
        btnGoalFitness = findViewById(R.id.btn_goal_fitness)
    }

    private fun setupToggleListeners() {
        // 성별 토글
        btnGenderMale.setOnClickListener { selectGender("남성") }
        btnGenderFemale.setOnClickListener { selectGender("여성") }

        // 목표 토글
        btnGoalMarathon.setOnClickListener { selectGoal("마라톤") }
        btnGoalDiet.setOnClickListener { selectGoal("다이어트") }
        btnGoalFitness.setOnClickListener { selectGoal("체력증진") }
    }

    private fun selectGender(gender: String) {
        selectedGender = gender
        if (gender == "남성") {
            btnGenderMale.setBackgroundResource(R.drawable.bg_toggle_selected)
            btnGenderMale.setTextColor(Color.parseColor("#FF6B35"))
            btnGenderFemale.setBackgroundResource(R.drawable.bg_toggle_unselected)
            btnGenderFemale.setTextColor(Color.parseColor("#888888"))
        } else {
            btnGenderFemale.setBackgroundResource(R.drawable.bg_toggle_selected)
            btnGenderFemale.setTextColor(Color.parseColor("#FF6B35"))
            btnGenderMale.setBackgroundResource(R.drawable.bg_toggle_unselected)
            btnGenderMale.setTextColor(Color.parseColor("#888888"))
        }
    }

    private fun selectGoal(goal: String) {
        selectedGoal = goal
        val buttons = listOf(
            btnGoalMarathon to "마라톤",
            btnGoalDiet to "다이어트",
            btnGoalFitness to "체력증진"
        )
        for ((btn, label) in buttons) {
            if (label == goal) {
                btn.setBackgroundResource(R.drawable.bg_toggle_selected)
                btn.setTextColor(Color.parseColor("#FF6B35"))
            } else {
                btn.setBackgroundResource(R.drawable.bg_toggle_unselected)
                btn.setTextColor(Color.parseColor("#888888"))
            }
        }
    }

    private fun loadCurrentUserProfile() {
        val userId = SessionManager.getUserId(this)

        RetrofitClient.api.getUserProfile(userId).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    currentUser = user

                    etName.setText(user.nickname)
                    user.height?.let {
                        // 소수점이 .0으로 끝나면 정수로 깔끔하게 표시
                        val hStr = if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                        etHeight.setText(hStr)
                    }
                    user.weight?.let {
                        val wStr = if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
                        etWeight.setText(wStr)
                    }
                    user.age?.let { etAge.setText(it.toString()) }

                    if (!user.gender.isNullOrBlank()) {
                        selectGender(user.gender)
                    }
                    if (!user.goal.isNullOrBlank()) {
                        selectGoal(user.goal)
                    }
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                Toast.makeText(this@EditProfileActivity, "기존 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveUserProfile() {
        val userId = SessionManager.getUserId(this)
        val name = etName.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val height = etHeight.text.toString().trim().toDoubleOrNull()
        val weight = etWeight.text.toString().trim().toDoubleOrNull()
        val age = etAge.text.toString().trim().toIntOrNull()

        val updatedUser = User(
            email = userId,
            password = currentUser?.password ?: "",
            nickname = name,
            height = height,
            weight = weight,
            age = age,
            gender = selectedGender,
            goal = selectedGoal,
            injury = currentUser?.injury
        )

        RetrofitClient.api.updateUserProfile(userId, updatedUser).enqueue(object : Callback<User> {
            override fun onResponse(call: Call<User>, response: Response<User>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@EditProfileActivity, "회원정보가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    finish() // 창을 닫고 마이페이지로 복귀
                } else {
                    Toast.makeText(this@EditProfileActivity, "수정 실패 (코드: ${response.code()})", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<User>, t: Throwable) {
                Toast.makeText(this@EditProfileActivity, "서버 통신 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}