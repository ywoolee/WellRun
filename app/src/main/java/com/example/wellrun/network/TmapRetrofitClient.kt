package com.example.wellrun.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object TmapRetrofitClient {
    private const val TMAP_BASE_URL = "https://apis.openapi.sk.com/"

    val api: TmapApiService by lazy {
        Retrofit.Builder()
            .baseUrl(TMAP_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmapApiService::class.java)
    }
}