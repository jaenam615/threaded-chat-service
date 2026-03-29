package com.example.threadedchatservice.dto.response

data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val name: String,
    val role: String,
)
