package com.example.wellrun.course

import android.content.Context
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import com.example.wellrun.network.TmapRetrofitClient

class TmapAutoCompleteAdapter(context: Context, private val appKey: String) :
    ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line), Filterable {

    override fun getFilter(): Filter {
        return object : Filter() {
            // 백그라운드 스레드에서 T맵 API 검색 수행
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null && constraint.length >= 2) {
                    try {
                        // T맵 서버로 검색어 전송 (동기 처리)
                        val call = TmapRetrofitClient.api.searchPlace(appKey, 1, constraint.toString())
                        val response = call.execute()

                        if (response.isSuccessful) {
                            val pois = response.body()?.searchPoiInfo?.pois?.poi
                            val suggestions = pois?.map { it.name } ?: emptyList()

                            filterResults.values = suggestions
                            filterResults.count = suggestions.size
                        } else {
                            Log.e("TmapAutoCompleteAdapter", "API 에러: ${response.code()} - ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e("TmapAutoCompleteAdapter", "통신 실패", e)
                    }
                }
                return filterResults
            }

            // 검색 결과를 메인 스레드 UI(드롭다운)에 갱신
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                clear() // 🔥 핵심: ArrayAdapter 내부 리스트 초기화
                if (results != null && results.count > 0) {
                    val resultList = results.values as List<String>
                    addAll(resultList) // 🔥 핵심: 새로운 검색 결과 통째로 붓기
                    notifyDataSetChanged() // 목록 띄우기
                } else {
                    notifyDataSetInvalidated() // 목록 닫기
                }
            }
        }
    }
}