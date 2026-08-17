package com.example.wellrun.auth  // <--- utils 대신 auth로 변경

import android.content.Context
import android.content.SharedPreferences

object SessionManager {
    private const val PREF_NAME = "WellRunPrefs"
    private const val KEY_USER_ID = "USER_ID"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserId(context: Context, userId: String) {
        getPreferences(context).edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(context: Context): String {
        return getPreferences(context).getString(KEY_USER_ID, "unknown_user") ?: "unknown_user"
    }

    fun clearSession(context: Context) {
        getPreferences(context).edit().clear().apply()
    }
}