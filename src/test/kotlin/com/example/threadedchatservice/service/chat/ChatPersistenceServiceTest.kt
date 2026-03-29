package com.example.threadedchatservice.service.chat

import com.example.threadedchatservice.dto.request.ChatCreateRequest
import com.example.threadedchatservice.entity.ChatEntity
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.entity.ThreadEntity
import com.example.threadedchatservice.entity.UserEntity
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import com.example.threadedchatservice.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional

class ChatPersistenceServiceTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var threadRepository: ThreadRepository
    private lateinit var userRepository: UserRepository
    private lateinit var chatPersistenceService: ChatPersistenceService

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
        userRepository = mock()
        chatPersistenceService = ChatPersistenceService(chatRepository, threadRepository, userRepository)
    }

    @Nested
    @DisplayName("prepareChat - 스레드 30분 규칙")
    inner class PrepareChatTest {

        @Test
        @DisplayName("첫 질문이면 새 스레드를 생성한다")
        fun `given first question, when prepareChat, then creates new thread`() {
            // given
            val request = ChatCreateRequest(question = "안녕하세요")
            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(threadRepository.findLatestByUserId(1L)).thenReturn(null)

            val newThread = ThreadEntity(id = 1L, user = user)
            whenever(threadRepository.save(any<ThreadEntity>())).thenReturn(newThread)
            whenever(chatRepository.findByThreadIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

            // when
            val result = chatPersistenceService.prepareChat(1L, request)

            // then
            verify(threadRepository).save(any<ThreadEntity>())
            assertEquals(1L, result.thread.id)
            assertEquals(1, result.messages.size)
            assertEquals("안녕하세요", result.messages[0]["content"])
        }

        @Test
        @DisplayName("30분 이내 질문이면 기존 스레드를 유지한다")
        fun `given question within 30min, when prepareChat, then reuses existing thread`() {
            // given
            val request = ChatCreateRequest(question = "후속 질문")
            val existingThread = ThreadEntity(id = 1L, user = user)
            val recentChat = ChatEntity(
                id = 1L,
                thread = existingThread,
                question = "이전 질문",
                answer = "이전 답변",
                createdAt = OffsetDateTime.now().minusMinutes(10),
            )

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(threadRepository.findLatestByUserId(1L)).thenReturn(existingThread)
            whenever(chatRepository.findLatestByThreadId(1L)).thenReturn(recentChat)
            whenever(chatRepository.findByThreadIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(recentChat))

            // when
            val result = chatPersistenceService.prepareChat(1L, request)

            // then
            verify(threadRepository, never()).save(any<ThreadEntity>())
            assertEquals(1L, result.thread.id)
            assertEquals(3, result.messages.size)
            assertEquals("이전 질문", result.messages[0]["content"])
            assertEquals("이전 답변", result.messages[1]["content"])
            assertEquals("후속 질문", result.messages[2]["content"])
        }

        @Test
        @DisplayName("30분 초과 후 질문이면 새 스레드를 생성한다")
        fun `given question after 30min, when prepareChat, then creates new thread`() {
            // given
            val request = ChatCreateRequest(question = "새 질문")
            val existingThread = ThreadEntity(id = 1L, user = user)
            val oldChat = ChatEntity(
                id = 1L,
                thread = existingThread,
                question = "오래전 질문",
                answer = "오래전 답변",
                createdAt = OffsetDateTime.now().minusMinutes(31),
            )

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(threadRepository.findLatestByUserId(1L)).thenReturn(existingThread)
            whenever(chatRepository.findLatestByThreadId(1L)).thenReturn(oldChat)

            val newThread = ThreadEntity(id = 2L, user = user)
            whenever(threadRepository.save(any<ThreadEntity>())).thenReturn(newThread)
            whenever(chatRepository.findByThreadIdOrderByCreatedAtAsc(2L)).thenReturn(emptyList())

            // when
            val result = chatPersistenceService.prepareChat(1L, request)

            // then
            verify(threadRepository).save(any<ThreadEntity>())
            assertEquals(2L, result.thread.id)
            assertEquals(1, result.messages.size)
        }

        @Test
        @DisplayName("스레드는 있지만 채팅이 없으면 기존 스레드를 재사용한다")
        fun `given thread with no chats, when prepareChat, then reuses thread`() {
            // given
            val request = ChatCreateRequest(question = "첫 질문")
            val emptyThread = ThreadEntity(id = 1L, user = user)

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(threadRepository.findLatestByUserId(1L)).thenReturn(emptyThread)
            whenever(chatRepository.findLatestByThreadId(1L)).thenReturn(null)
            whenever(chatRepository.findByThreadIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())

            // when
            val result = chatPersistenceService.prepareChat(1L, request)

            // then
            verify(threadRepository, never()).save(any<ThreadEntity>())
            assertEquals(1L, result.thread.id)
        }

        @Test
        @DisplayName("존재하지 않는 유저면 예외를 던진다")
        fun `given non-existent user, when prepareChat, then throws exception`() {
            // given
            val request = ChatCreateRequest(question = "질문")
            whenever(userRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                chatPersistenceService.prepareChat(999L, request)
            }
            assertEquals("User not found", exception.message)
        }
    }
}
