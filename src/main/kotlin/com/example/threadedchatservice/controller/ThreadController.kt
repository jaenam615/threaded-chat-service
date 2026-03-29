package com.example.threadedchatservice.controller

import com.example.threadedchatservice.service.thread.ThreadService
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/threads")
class ThreadController(
    private val threadService: ThreadService,
) {

    @DeleteMapping("/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteThread(
        authentication: Authentication,
        @PathVariable threadId: Long,
    ) {
        val userId = authentication.principal as Long
        val role = authentication.authorities.first().authority.removePrefix("ROLE_").lowercase()
        threadService.deleteThread(userId, role, threadId)
    }
}
