package com.example.threadedchatservice.dto.response

import java.time.OffsetDateTime

data class ChatResponse(
    val id: Long,
    val threadId: Long,
    val question: String,
    val answer: String,
    val createdAt: OffsetDateTime,
)
