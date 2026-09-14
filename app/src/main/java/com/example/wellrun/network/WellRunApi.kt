package com.example.wellrun.network

import com.example.wellrun.model.RunningRecord
import com.example.wellrun.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ✨ 서버의 JSON 응답을 받을 데이터 클래스들
data class RouteNode(
    val lat: Double,
    val lng: Double,
    val elevation: Double
)

data class CourseResponse(
    val status: String,
    val message: String?,
    val metrics: Map<String, Any>?,
    val path: List<RouteNode>? // 핵심: A* 알고리즘이 찾은 징검다리 배열
)

interface WellRunApi {
    @POST("/api/users/signup")
    fun signUp(@Body user: User): Call<String>

    @POST("/api/users/login")
    fun login(@Body user: User): Call<String>

    @POST("/api/running/record")
    fun saveRunningRecord(@Body record: RunningRecord): Call<String>

    // ✨ 러닝 코스 생성 GET 요청 API 추가 (파라미터 2개 추가)
    @GET("/api/course/generate-course")
    fun generateCourse(
        @Query("startLat") startLat: Double,
        @Query("startLng") startLng: Double,
        @Query("endLat") endLat: Double,
        @Query("endLng") endLng: Double,
        @Query("targetDistance") targetDistance: Double,
        @Query("isFlat") isFlat: Boolean
    ): Call<CourseResponse>
}