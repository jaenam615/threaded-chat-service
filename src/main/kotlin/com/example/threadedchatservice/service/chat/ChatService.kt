package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.client.ClaudeClient
import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.dto.response.ChatResponse
import com.example.threadedchatservice.dto.response.ThreadWithChatsResponse
import com.example.threadedchatservice.entity.ChatEntity
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.entity.ThreadEntity
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import com.example.threadedchatservice.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val userRepository: UserRepository,
    private val claudeClient: ClaudeClient,
) {
    companion object {
        private const val THREAD_TIMEOUT_MINUTES = 30L
    }

    @Transactional
    fun createChat(
        userId: Long,
        request: ChatCreateRequest,
    ): ChatResponse {
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

        val answer = claudeClient.call(messages)

        val chat =
            chatRepository.save(
                ChatEntity(
                    thread = thread,
                    question = request.question,
                    answer = answer,
                ),
            )

        return ChatResponse(
            id = chat.id,
            threadId = thread.id,
            question = chat.question,
            answer = chat.answer,
            createdAt = chat.createdAt,
        )
    }

    private fun getOrCreateThread(
        userId: Long,
        user: com.example.threadedchatservice.entity.UserEntity,
    ): ThreadEntity {
        val latestThread = threadRepository.findLatestByUserId(userId)

        if (latestThread != null) {
            val latestChat = chatRepository.findLatestByThreadId(latestThread.id)

            if (latestChat != null) {
                val timeSinceLastChat =
                    java.time.Duration
                        .between(
                            latestChat.createdAt,
                            LocalDateTime.now(),
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

    fun getChats(
        userId: Long,
        role: String,
        pageable: Pageable,
    ): Page<ThreadWithChatsResponse> {
        val threads =
            if (role == Role.ADMIN.name) {
                threadRepository.findAll(pageable)
            } else {
                threadRepository.findByUserId(userId, pageable)
            }

        val threadIds = threads.content.map { it.id }
        val allChats = chatRepository.findByThreadIdInOrderByCreatedAtAsc(threadIds)
        val chatsByThreadId = allChats.groupBy { it.thread.id }

        return threads.map { thread ->
            ThreadWithChatsResponse(
                threadId = thread.id,
                createdAt = thread.createdAt,
                chats = (chatsByThreadId[thread.id] ?: emptyList()).map { chat ->
                    ChatResponse(
                        id = chat.id,
                        threadId = thread.id,
                        question = chat.question,
                        answer = chat.answer,
                        createdAt = chat.createdAt,
                    )
                },
            )
        }
    }
}
