package com.example.wellrun.model

data class User(
    val email: String,
    val password: String,
    val nickname: String,
    val height: Double? = null,
    val weight: Double? = null,
    val age: Int? = null,
    val gender: String? = null,
    val goal: String? = null,
    val injury: String? = null
)