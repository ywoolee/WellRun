package com.example.wellrun.model

import com.google.gson.annotations.SerializedName

// 서버와 주고받을 러닝 기록 택배 상자 (DTO)
data class RunningRecord(
    @SerializedName("userId") val userId: String,
    @SerializedName("distance") val distance: Double,
    @SerializedName("durationSeconds") val durationSeconds: Int,
    @SerializedName("averagePace") val averagePace: String,
    @SerializedName("averageHeartRate") val averageHeartRate: Int,

    // 리스트 형태의 데이터들은 통신 효율을 위해 JSON String으로 변환해서 담습니다.
    @SerializedName("splitsJson") val splitsJson: String,
    @SerializedName("routeJson") val routeJson: String
)