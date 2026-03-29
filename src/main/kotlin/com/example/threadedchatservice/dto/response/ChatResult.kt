package com.example.threadedchatservice.dto.response

import reactor.core.publisher.Flux

sealed class ChatResult {
    data class Complete(val response: ChatResponse) : ChatResult()
    data class Stream(val chunks: Flux<String>) : ChatResult()
}
