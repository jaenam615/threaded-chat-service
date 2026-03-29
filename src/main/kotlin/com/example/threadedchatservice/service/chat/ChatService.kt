package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.client.ClaudeClient
import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.dto.response.ChatResponse
import com.example.threadedchatservice.dto.response.ChatResult
import com.example.threadedchatservice.dto.response.ThreadWithChatsResponse
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import reactor.core.scheduler.Schedulers

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val threadRepository: ThreadRepository,
    private val chatPersistenceService: ChatPersistenceService,
    private val claudeClient: ClaudeClient,
) {
    fun createChat(
        userId: Long,
        request: ChatCreateRequest,
    ): ChatResult {
        val prepared = chatPersistenceService.prepareChat(userId, request)

        if (request.isStreaming) {
            val answerBuilder = StringBuilder()

            val chunks = claudeClient.callStream(prepared.messages, request.model)
                .doOnNext { chunk -> answerBuilder.append(chunk) }
                .doOnComplete {
                    reactor.core.publisher.Mono.fromCallable {
                        chatPersistenceService.saveChat(prepared.thread, request.question, answerBuilder.toString())
                    }.subscribeOn(Schedulers.boundedElastic()).subscribe()
                }

            return ChatResult.Stream(chunks)
        }

        val answer = claudeClient.call(prepared.messages, request.model)
        val chat = chatPersistenceService.saveChat(prepared.thread, request.question, answer)

        return ChatResult.Complete(
            ChatResponse(
                id = chat.id,
                threadId = prepared.thread.id,
                question = chat.question,
                answer = chat.answer,
                createdAt = chat.createdAt,
            ),
        )
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
