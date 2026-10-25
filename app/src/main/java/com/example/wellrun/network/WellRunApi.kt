package com.example.wellrun.network

import com.example.wellrun.model.RunningRecord
import com.example.wellrun.model.User
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class RouteNode(
    val lat: Double,
    val lng: Double,
    val elevation: Double
)

data class CourseResponse(
    val status: String,
    val message: String?,
    val metrics: Map<String, Any>?,
    val path: List<RouteNode>?
)

interface WellRunApi {
    @POST("/api/users/signup")
    fun signUp(@Body user: User): Call<String>

    @POST("/api/users/login")
    fun login(@Body user: User): Call<String>

    @POST("/api/running/record")
    fun saveRunningRecord(@Body record: RunningRecord): Call<String>

    // 내 러닝 기록 리스트 조회
    @GET("/api/running/history/{userId}")
    fun getRunningHistory(@Path("userId") userId: String): Call<List<RunningRecord>>

    // 내 유저 프로필 조회
    @GET("/api/users/{email}")
    fun getUserProfile(@Path("email") email: String): Call<User>

    // ✨ [신규 추가] 내 유저 프로필 수정
    @PUT("/api/users/{email}")
    fun updateUserProfile(@Path("email") email: String, @Body user: User): Call<User>

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