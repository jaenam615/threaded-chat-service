package com.example.threadedchatservice.dto.response

import java.time.LocalDateTime

data class ThreadWithChatsResponse(
    val threadId: Long,
    val createdAt: LocalDateTime,
    val chats: List<ChatResponse>,
)
