package com.example.threadedchatservice.config

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(e.message ?: "Not found"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(message))
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(e: ObjectOptimisticLockingFailureException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse("데이터가 다른 요청에 의해 변경되었습니다. 다시 시도해주세요."))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(e.message ?: "Service unavailable"))

    @ExceptionHandler(WebClientResponseException::class)
    fun handleWebClientError(e: WebClientResponseException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse("LLM API error: ${e.statusCode} - ${e.responseBodyAsString}"))

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(e.message ?: "Internal server error"))
}

data class ErrorResponse(
    val error: String,
)
