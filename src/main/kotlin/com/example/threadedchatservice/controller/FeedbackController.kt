package com.example.threadedchatservice.controller

import com.example.threadedchatservice.dto.request.FeedbackCreateRequest
import com.example.threadedchatservice.dto.request.FeedbackStatusRequest
import com.example.threadedchatservice.dto.response.FeedbackResponse
import com.example.threadedchatservice.service.feedback.FeedbackService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/feedbacks")
class FeedbackController(
    private val feedbackService: FeedbackService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createFeedback(
        authentication: Authentication,
        @Valid @RequestBody request: FeedbackCreateRequest,
    ): FeedbackResponse {
        val userId = authentication.principal as Long
        val role =
            authentication.authorities
                .first()
                .authority
                .removePrefix("ROLE_")
        return feedbackService.createFeedback(userId, role, request)
    }

    @GetMapping
    fun getFeedbacks(
        authentication: Authentication,
        @RequestParam(required = false) isPositive: Boolean?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): Page<FeedbackResponse> {
        val userId = authentication.principal as Long
        val role =
            authentication.authorities
                .first()
                .authority
                .removePrefix("ROLE_")
        return feedbackService.getFeedbacks(userId, role, isPositive, pageable)
    }

    @PatchMapping("/{feedbackId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateStatus(
        @PathVariable feedbackId: Long,
        @RequestBody request: FeedbackStatusRequest,
    ): FeedbackResponse = feedbackService.updateStatus(feedbackId, request.status)
}
