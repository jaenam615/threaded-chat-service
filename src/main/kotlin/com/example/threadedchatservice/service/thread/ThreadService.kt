package com.example.threadedchatservice.service.thread

import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.FeedbackRepository
import com.example.threadedchatservice.repository.ThreadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ThreadService(
    private val threadRepository: ThreadRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
) {
    @Transactional
    fun deleteThread(
        userId: Long,
        role: String,
        threadId: Long,
    ) {
        val thread =
            threadRepository
                .findById(threadId)
                .orElseThrow { IllegalArgumentException("Thread not found") }

        if (role != Role.ADMIN.name && thread.user.id != userId) {
            throw IllegalArgumentException("Not authorized to delete this thread")
        }

        val chats = chatRepository.findByThreadId(threadId)
        val chatIds = chats.map { it.id }

        if (chatIds.isNotEmpty()) {
            feedbackRepository.deleteByChatIdIn(chatIds)
        }
        chatRepository.deleteAll(chats)

        threadRepository.delete(thread)
    }
}
