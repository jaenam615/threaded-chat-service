package com.example.threadedchatservice.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import java.time.Duration

@Component
class ClaudeClient(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    @Value("\${anthropic.api-key}") private val apiKey: String,
) : LlmClient {
    companion object {
        private const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
    }

    override fun call(prompt: String): String = call(listOf(mapOf("role" to "user", "content" to prompt)))

    @CircuitBreaker(name = "claudeApi", fallbackMethod = "callFallback")
    fun call(
        messages: List<Map<String, String>>,
        model: String? = null,
    ): String {
        val requestBody =
            mapOf(
                "model" to (model ?: DEFAULT_MODEL),
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
                .timeout(Duration.ofSeconds(10))
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

    @CircuitBreaker(name = "claudeApi", fallbackMethod = "callStreamFallback")
    fun callStream(
        messages: List<Map<String, String>>,
        model: String? = null,
    ): Flux<String> {
        val requestBody =
            mapOf(
                "model" to (model ?: DEFAULT_MODEL),
                "max_tokens" to 1024,
                "messages" to messages,
                "stream" to true,
            )

        return webClient
            .post()
            .uri("https://api.anthropic.com/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToFlux(ServerSentEvent::class.java)
            .filter { it.event() == "content_block_delta" }
            .mapNotNull { event ->
                try {
                    val data = objectMapper.readValue(event.data() as String, Map::class.java)
                    val delta = data["delta"] as? Map<*, *>
                    delta?.get("text") as? String
                } catch (e: Exception) {
                    null
                }
            }
    }

    @Suppress("unused")
    private fun callFallback(
        messages: List<Map<String, String>>,
        model: String?,
        e: Exception,
    ): String = throw IllegalStateException("AI 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", e)

    @Suppress("unused")
    private fun callStreamFallback(
        messages: List<Map<String, String>>,
        model: String?,
        e: Exception,
    ): Flux<String> = Flux.error(IllegalStateException("AI 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", e))

    private fun extractText(response: Map<*, *>?): String {
        if (response == null) return "응답 없음"

        val content = response["content"] as? List<*> ?: return "응답 없음"
        val first = content.firstOrNull() as? Map<*, *> ?: return "응답 없음"

        return first["text"] as? String ?: "응답 없음"
    }
}
