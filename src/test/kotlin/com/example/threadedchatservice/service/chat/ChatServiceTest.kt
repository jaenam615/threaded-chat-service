package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.client.ClaudeClient
import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.dto.response.ChatResult
import com.example.threadedchatservice.entity.ChatEntity
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.entity.ThreadEntity
import com.example.threadedchatservice.entity.UserEntity
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChatServiceTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var threadRepository: ThreadRepository
    private lateinit var chatPersistenceService: ChatPersistenceService
    private lateinit var claudeClient: ClaudeClient
    private lateinit var chatService: ChatService

    private val user = UserEntity(
        id = 1L,
        email = "test@test.com",
        password = "encoded",
        name = "테스트",
        role = Role.MEMBER,
    )

    @BeforeEach
    fun setUp() {
        chatRepository = mock()
        threadRepository = mock()
        chatPersistenceService = mock()
        claudeClient = mock()
        chatService = ChatService(chatRepository, threadRepository, chatPersistenceService, claudeClient)
    }

    @Nested
    @DisplayName("createChat - 대화 생성")
    inner class CreateChatTest {

        @Test
        @DisplayName("일반 요청 시 ChatResult.Complete를 반환한다")
        fun `given non-streaming request, when createChat, then returns Complete`() {
            // given
            val request = ChatCreateRequest(question = "질문", isStreaming = false)
            val thread = ThreadEntity(id = 1L, user = user)
            val prepared = ChatPersistenceService.PreparedChat(
                thread = thread,
                messages = listOf(mapOf("role" to "user", "content" to "질문")),
            )
            val savedChat = ChatEntity(id = 1L, thread = thread, question = "질문", answer = "답변")

            whenever(chatPersistenceService.prepareChat(1L, request)).thenReturn(prepared)
            whenever(claudeClient.call(any<List<Map<String, String>>>(), org.mockito.kotlin.isNull())).thenReturn("답변")
            whenever(chatPersistenceService.saveChat(thread, "질문", "답변")).thenReturn(savedChat)

            // when
            val result = chatService.createChat(1L, request)

            // then
            assertTrue(result is ChatResult.Complete)
            val complete = result as ChatResult.Complete
            assertEquals("질문", complete.response.question)
            assertEquals("답변", complete.response.answer)
        }

        @Test
        @DisplayName("스트리밍 요청 시 ChatResult.Stream을 반환한다")
        fun `given streaming request, when createChat, then returns Stream`() {
            // given
            val request = ChatCreateRequest(question = "질문", isStreaming = true)
            val thread = ThreadEntity(id = 1L, user = user)
            val prepared = ChatPersistenceService.PreparedChat(
                thread = thread,
                messages = listOf(mapOf("role" to "user", "content" to "질문")),
            )

            whenever(chatPersistenceService.prepareChat(1L, request)).thenReturn(prepared)
            whenever(claudeClient.callStream(any(), org.mockito.kotlin.isNull())).thenReturn(reactor.core.publisher.Flux.just("청크1", "청크2"))

            // when
            val result = chatService.createChat(1L, request)

            // then
            assertTrue(result is ChatResult.Stream)
        }

        @Test
        @DisplayName("model 파라미터가 ClaudeClient에 전달된다")
        fun `given model parameter, when createChat, then passes model to client`() {
            // given
            val request = ChatCreateRequest(question = "질문", model = "claude-haiku-4-5-20251001")
            val thread = ThreadEntity(id = 1L, user = user)
            val prepared = ChatPersistenceService.PreparedChat(
                thread = thread,
                messages = listOf(mapOf("role" to "user", "content" to "질문")),
            )
            val savedChat = ChatEntity(id = 1L, thread = thread, question = "질문", answer = "답변")

            whenever(chatPersistenceService.prepareChat(1L, request)).thenReturn(prepared)
            whenever(claudeClient.call(any<List<Map<String, String>>>(), org.mockito.kotlin.eq("claude-haiku-4-5-20251001"))).thenReturn("답변")
            whenever(chatPersistenceService.saveChat(thread, "질문", "답변")).thenReturn(savedChat)

            // when
            chatService.createChat(1L, request)

            // then
            verify(claudeClient).call(any<List<Map<String, String>>>(), org.mockito.kotlin.eq("claude-haiku-4-5-20251001"))
        }
    }
}
