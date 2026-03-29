package com.example.threadedchatservice.dto.request

import jakarta.validation.constraints.NotNull

data class FeedbackCreateRequest(
    @field:NotNull(message = "Chat ID is required")
    val chatId: Long,

    @field:NotNull(message = "isPositive is required")
    val isPositive: Boolean,
)

data class FeedbackStatusRequest(
    val status: String,
)
