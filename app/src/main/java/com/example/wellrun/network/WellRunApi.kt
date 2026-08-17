package com.example.wellrun.network

import com.example.wellrun.model.RunningRecord
import com.example.wellrun.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface WellRunApi {

    // 회원가입 요청
    @POST("/api/users/signup")
    fun signUp(@Body user: User): Call<String>

    // 로그인 요청
    @POST("/api/users/login")
    fun login(@Body user: User): Call<String>

    // ✨ 새로 추가: 러닝 기록 저장 요청
    @POST("/api/running/record")
    fun saveRunningRecord(@Body record: RunningRecord): Call<String>
}