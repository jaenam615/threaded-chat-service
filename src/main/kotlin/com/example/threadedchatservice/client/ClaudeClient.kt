package com.example.threadedchatservice.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import java.time.Duration

@Component
class ClaudeClient(
    private val webClient: WebClient,
    @Value("\${anthropic.api-key}") private val apiKey: String,
) : LlmClient {
    override fun call(prompt: String): String = call(listOf(mapOf("role" to "user", "content" to prompt)))

    fun call(messages: List<Map<String, String>>): String {
        val requestBody =
            mapOf(
                "model" to "claude-sonnet-4-20250514",
                "max_tokens" to 1024,
                "messages" to messages,
            )

        val response =
            webClient
                .post()
                .uri("https://api.anthropic.com/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map::class.java)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(
                    Retry
                        .backoff(3, Duration.ofMillis(500))
                        .filter {
                            it is WebClientResponseException.ServiceUnavailable ||
                                it is WebClientResponseException.TooManyRequests
                        },
                ).block()

        return extractText(response)
    }

    private fun extractText(response: Map<*, *>?): String {
        if (response == null) return "응답 없음"

        val content = response["content"] as? List<*> ?: return "응답 없음"
        val first = content.firstOrNull() as? Map<*, *> ?: return "응답 없음"

        return first["text"] as? String ?: "응답 없음"
    }
}
