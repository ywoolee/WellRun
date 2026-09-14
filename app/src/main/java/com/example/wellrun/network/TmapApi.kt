package com.example.wellrun.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// 1. T맵 POI(장소) 검색 응답 데이터 클래스
data class TmapSearchResponse(
    val searchPoiInfo: SearchPoiInfo?
)

data class SearchPoiInfo(
    val pois: Pois?
)

data class Pois(
    val poi: List<TmapPoi>?
)

data class TmapPoi(
    val name: String,
    val frontLat: String, // 위도 (Latitude)
    val frontLon: String  // 경도 (Longitude)
)

// 2. T맵 검색 API 호출 인터페이스
interface TmapApiService {
    @GET("tmap/pois")
    fun searchPlace(
        @Header("appKey") appKey: String, // T맵은 Header에 appKey를 넣습니다
        @Query("version") version: Int = 1,
        @Query("searchKeyword") searchKeyword: String
    ): Call<TmapSearchResponse>
}