package com.example.threadedchatservice.dto.request

import jakarta.validation.constraints.NotBlank

data class ChatCreateRequest(
    @field:NotBlank(message = "Question is required")
    val question: String,

    val isStreaming: Boolean = false,

    val model: String? = null,
)
