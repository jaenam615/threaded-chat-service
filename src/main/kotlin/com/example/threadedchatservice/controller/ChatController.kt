package com.example.threadedchatservice.controller

import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.dto.response.ChatResult
import com.example.threadedchatservice.dto.response.ThreadWithChatsResponse
import com.example.threadedchatservice.service.chat.ChatService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/chats")
class ChatController(
    private val chatService: ChatService,
) {
    @PostMapping
    fun createChat(
        authentication: Authentication,
        @Valid @RequestBody request: ChatCreateRequest,
    ): ResponseEntity<*> {
        val userId = authentication.principal as Long

        return when (val result = chatService.createChat(userId, request)) {
            is ChatResult.Complete -> ResponseEntity.status(HttpStatus.CREATED).body(result.response)
            is ChatResult.Stream -> ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(result.chunks)
        }
    }

    @GetMapping
    fun getChats(
        authentication: Authentication,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<ThreadWithChatsResponse> {
        val userId = authentication.principal as Long
        val role = authentication.authorities.first().authority.removePrefix("ROLE_")
        return chatService.getChats(userId, role, pageable)
    }
}
