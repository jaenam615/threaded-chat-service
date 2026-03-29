package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.client.LlmClient
import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.dto.response.ChatResponse
import com.example.threadedchatservice.entity.ChatEntity
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
    private val llmClient: LlmClient,
) {
    companion object {
        private const val THREAD_TIMEOUT_MINUTES = 30L
    }

    @Transactional
    fun createChat(userId: Long, request: ChatCreateRequest): ChatResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }

        // 30분 규칙: 스레드 결정
        val thread = getOrCreateThread(userId, user)

        // LLM 호출
        val answer = llmClient.call(request.question)

        // Chat 저장
        val chat = chatRepository.save(
            ChatEntity(
                thread = thread,
                question = request.question,
                answer = answer,
            )
        )

        return ChatResponse(
            id = chat.id,
            threadId = thread.id,
            question = chat.question,
            answer = chat.answer,
            createdAt = chat.createdAt,
        )
    }

    private fun getOrCreateThread(userId: Long, user: com.example.threadedchatservice.entity.UserEntity): ThreadEntity {
        val latestThread = threadRepository.findLatestByUserId(userId)

        if (latestThread != null) {
            val latestChat = chatRepository.findLatestByThreadId(latestThread.id)

            if (latestChat != null) {
                val timeSinceLastChat = java.time.Duration.between(
                    latestChat.createdAt,
                    LocalDateTime.now()
                ).toMinutes()

                if (timeSinceLastChat < THREAD_TIMEOUT_MINUTES) {
                    return latestThread
                }
            } else {
                // 스레드는 있지만 채팅이 없으면 기존 스레드 사용
                return latestThread
            }
        }

        // 새 스레드 생성
        return threadRepository.save(ThreadEntity(user = user))
    }

    fun getChats(userId: Long, role: String, pageable: Pageable): Page<ChatResponse> {
        val chats = if (role == "admin") {
            chatRepository.findAll(pageable)
        } else {
            chatRepository.findByUserId(userId, pageable)
        }

        return chats.map { chat ->
            ChatResponse(
                id = chat.id,
                threadId = chat.thread.id,
                question = chat.question,
                answer = chat.answer,
                createdAt = chat.createdAt,
            )
        }
    }
}
