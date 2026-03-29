package com.example.threadedchatservice.service.feedback

import com.example.threadedchatservice.dto.request.FeedbackCreateRequest
import com.example.threadedchatservice.dto.response.FeedbackResponse
import com.example.threadedchatservice.entity.FeedbackEntity
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.FeedbackRepository
import com.example.threadedchatservice.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createFeedback(
        userId: Long,
        role: String,
        request: FeedbackCreateRequest,
    ): FeedbackResponse {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("User not found") }

        val chat =
            chatRepository
                .findById(request.chatId)
                .orElseThrow { IllegalArgumentException("Chat not found") }

        if (role != Role.ADMIN.name && chat.thread.user.id != userId) {
            throw IllegalArgumentException("Not authorized to feedback this chat")
        }

        if (feedbackRepository.existsByUserIdAndChatId(userId, request.chatId)) {
            throw IllegalArgumentException("Feedback already exists for this chat")
        }

        val feedback =
            feedbackRepository.save(
                FeedbackEntity(
                    user = user,
                    chat = chat,
                    isPositive = request.isPositive,
                ),
            )

        return toResponse(feedback)
    }

    fun getFeedbacks(
        userId: Long,
        role: String,
        isPositive: Boolean?,
        pageable: Pageable,
    ): Page<FeedbackResponse> {
        val feedbacks =
            when {
                role == Role.ADMIN.name && isPositive != null -> feedbackRepository.findByIsPositive(isPositive, pageable)
                role == Role.ADMIN.name -> feedbackRepository.findAll(pageable)
                isPositive != null -> feedbackRepository.findByUserIdAndIsPositive(userId, isPositive, pageable)
                else -> feedbackRepository.findByUserId(userId, pageable)
            }

        return feedbacks.map { toResponse(it) }
    }

    @Transactional
    fun updateStatus(
        feedbackId: Long,
        status: String,
    ): FeedbackResponse {
        val feedback =
            feedbackRepository
                .findById(feedbackId)
                .orElseThrow { IllegalArgumentException("Feedback not found") }

        if (status !in listOf("pending", "resolved")) {
            throw IllegalArgumentException("Invalid status: $status")
        }

        feedback.status = status
        return toResponse(feedbackRepository.save(feedback))
    }

    private fun toResponse(feedback: FeedbackEntity) =
        FeedbackResponse(
            id = feedback.id,
            userId = feedback.user.id,
            chatId = feedback.chat.id,
            isPositive = feedback.isPositive,
            status = feedback.status,
            createdAt = feedback.createdAt,
        )
}
