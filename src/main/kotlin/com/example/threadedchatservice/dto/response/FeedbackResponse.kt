package com.example.threadedchatservice.dto.response

import java.time.LocalDateTime

data class FeedbackResponse(
    val id: Long,
    val userId: Long,
    val chatId: Long,
    val isPositive: Boolean,
    val status: String,
    val createdAt: LocalDateTime,
)
