package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.entity.ChatEntity
import com.example.threadedchatservice.entity.ThreadEntity
import com.example.threadedchatservice.entity.UserEntity
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import com.example.threadedchatservice.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ChatPersistenceService(
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private const val THREAD_TIMEOUT_MINUTES = 30L
    }

    @Transactional
    fun prepareChat(userId: Long, request: ChatCreateRequest): PreparedChat {
        val user =
            userRepository
                .findById(userId)
                .orElseThrow { IllegalArgumentException("User not found") }

        val thread = getOrCreateThread(userId, user)

        val previousChats = chatRepository.findByThreadIdOrderByCreatedAtAsc(thread.id)

        val messages =
            previousChats.flatMap { chat ->
                listOf(
                    mapOf("role" to "user", "content" to chat.question),
                    mapOf("role" to "assistant", "content" to chat.answer),
                )
            } + mapOf("role" to "user", "content" to request.question)

        return PreparedChat(thread = thread, messages = messages)
    }

    @Transactional
    fun saveChat(thread: ThreadEntity, question: String, answer: String): ChatEntity {
        return chatRepository.save(
            ChatEntity(
                thread = thread,
                question = question,
                answer = answer,
            ),
        )
    }

    private fun getOrCreateThread(
        userId: Long,
        user: UserEntity,
    ): ThreadEntity {
        val latestThread = threadRepository.findLatestByUserId(userId)

        if (latestThread != null) {
            val latestChat = chatRepository.findLatestByThreadId(latestThread.id)

            if (latestChat != null) {
                val timeSinceLastChat =
                    java.time.Duration
                        .between(
                            latestChat.createdAt,
                            OffsetDateTime.now(),
                        ).toMinutes()

                if (timeSinceLastChat < THREAD_TIMEOUT_MINUTES) {
                    return latestThread
                }
            } else {
                return latestThread
            }
        }

        return threadRepository.save(ThreadEntity(user = user))
    }

    data class PreparedChat(
        val thread: ThreadEntity,
        val messages: List<Map<String, String>>,
    )
}
