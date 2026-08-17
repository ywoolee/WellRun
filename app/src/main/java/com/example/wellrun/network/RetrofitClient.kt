package com.example.wellrun.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory // ✨ import 추가

object RetrofitClient {
    private const val BASE_URL = "https://boastful-spookily-satisfy.ngrok-free.dev/" // (ngrok 주소 그대로 사용)

    val api: WellRunApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // ✨ ScalarsConverterFactory를 먼저 추가해서 String 처리를 우선하게 합니다!
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WellRunApi::class.java)
    }
}