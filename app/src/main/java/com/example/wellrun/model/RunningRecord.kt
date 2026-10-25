package com.example.wellrun.model

import com.google.gson.annotations.SerializedName

// 서버와 주고받을 러닝 기록 택배 상자 (DTO)
data class RunningRecord(
    @SerializedName("userId") val userId: String,
    @SerializedName("distance") val distance: Double,
    @SerializedName("durationSeconds") val durationSeconds: Int,
    @SerializedName("averagePace") val averagePace: String,
    @SerializedName("averageHeartRate") val averageHeartRate: Int,
    @SerializedName("totalElevation") val totalElevation: Double,
    @SerializedName("averageCadence") val averageCadence: Int,
    @SerializedName("splitsJson") val splitsJson: String,
    @SerializedName("routeJson") val routeJson: String,

    // ✨ 서버 DB의 생성 날짜(예: "2026-10-25T14:30:00")를 받기 위해 추가!
    @SerializedName("createdAt") val createdAt: String? = null
)