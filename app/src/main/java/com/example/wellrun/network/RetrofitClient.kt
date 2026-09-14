package com.example.wellrun.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory // ✨ import 추가
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://boastful-spookily-satisfy.ngrok-free.dev/" // (ngrok 주소 그대로 사용)

    // ✨ 타임아웃을 기본 10초에서 60초로 대폭 늘린 OkHttpClient 생성
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // 서버 접속 대기 시간
        .readTimeout(60, TimeUnit.SECONDS)    // 응답 수신 대기 시간
        .writeTimeout(60, TimeUnit.SECONDS)   // 데이터 전송 대기 시간
        .build()

    val api: WellRunApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            // ✨ ScalarsConverterFactory를 먼저 추가해서 String 처리를 우선하게 합니다!
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WellRunApi::class.java)
    }
}