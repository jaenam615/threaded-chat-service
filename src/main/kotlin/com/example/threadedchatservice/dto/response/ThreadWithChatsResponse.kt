package com.example.threadedchatservice.dto.response

import java.time.OffsetDateTime

data class ThreadWithChatsResponse(
    val threadId: Long,
    val createdAt: OffsetDateTime,
    val chats: List<ChatResponse>,
)
