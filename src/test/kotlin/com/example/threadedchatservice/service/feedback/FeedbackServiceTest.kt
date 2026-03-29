package com.example.threadedchatservice.service.feedback

import com.example.threadedchatservice.dto.request.FeedbackCreateRequest
import com.example.threadedchatservice.entity.ChatEntity
import com.example.threadedchatservice.entity.FeedbackEntity
import com.example.threadedchatservice.entity.FeedbackStatus
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.entity.ThreadEntity
import com.example.threadedchatservice.entity.UserEntity
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.FeedbackRepository
import com.example.threadedchatservice.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class FeedbackServiceTest {

    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var userRepository: UserRepository
    private lateinit var feedbackService: FeedbackService

    private val user = UserEntity(
        id = 1L,
        email = "test@test.com",
        password = "encoded",
        name = "테스트",
        role = Role.MEMBER,
    )

    private val adminUser = UserEntity(
        id = 2L,
        email = "admin@test.com",
        password = "encoded",
        name = "관리자",
        role = Role.ADMIN,
    )

    private val thread = ThreadEntity(id = 1L, user = user)

    private val chat = ChatEntity(
        id = 1L,
        thread = thread,
        question = "질문",
        answer = "답변",
    )

    @BeforeEach
    fun setUp() {
        feedbackRepository = mock()
        chatRepository = mock()
        userRepository = mock()
        feedbackService = FeedbackService(feedbackRepository, chatRepository, userRepository)
    }

    @Nested
    @DisplayName("createFeedback - 피드백 생성")
    inner class CreateFeedbackTest {

        @Test
        @DisplayName("본인 대화에 피드백을 생성할 수 있다")
        fun `given own chat, when createFeedback, then succeeds`() {
            // given
            val request = FeedbackCreateRequest(chatId = 1L, isPositive = true)
            val savedFeedback = FeedbackEntity(id = 1L, user = user, chat = chat, isPositive = true)

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(chatRepository.findById(1L)).thenReturn(Optional.of(chat))
            whenever(feedbackRepository.existsByUserIdAndChatId(1L, 1L)).thenReturn(false)
            whenever(feedbackRepository.save(any<FeedbackEntity>())).thenReturn(savedFeedback)

            // when
            val result = feedbackService.createFeedback(1L, Role.MEMBER.name, request)

            // then
            assertEquals(true, result.isPositive)
            assertEquals("pending", result.status)
            assertEquals(1L, result.chatId)
        }

        @Test
        @DisplayName("다른 유저의 대화에는 피드백을 생성할 수 없다")
        fun `given other user's chat, when createFeedback, then throws exception`() {
            // given
            val otherUser = UserEntity(id = 99L, email = "other@test.com", password = "encoded", name = "다른유저")
            val request = FeedbackCreateRequest(chatId = 1L, isPositive = true)

            whenever(userRepository.findById(99L)).thenReturn(Optional.of(otherUser))
            whenever(chatRepository.findById(1L)).thenReturn(Optional.of(chat))

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                feedbackService.createFeedback(99L, Role.MEMBER.name, request)
            }
            assertEquals("Not authorized to feedback this chat", exception.message)
        }

        @Test
        @DisplayName("관리자는 모든 대화에 피드백을 생성할 수 있다")
        fun `given admin user, when createFeedback on any chat, then succeeds`() {
            // given
            val request = FeedbackCreateRequest(chatId = 1L, isPositive = false)
            val savedFeedback = FeedbackEntity(id = 2L, user = adminUser, chat = chat, isPositive = false)

            whenever(userRepository.findById(2L)).thenReturn(Optional.of(adminUser))
            whenever(chatRepository.findById(1L)).thenReturn(Optional.of(chat))
            whenever(feedbackRepository.existsByUserIdAndChatId(2L, 1L)).thenReturn(false)
            whenever(feedbackRepository.save(any<FeedbackEntity>())).thenReturn(savedFeedback)

            // when
            val result = feedbackService.createFeedback(2L, Role.ADMIN.name, request)

            // then
            assertEquals(false, result.isPositive)
        }

        @Test
        @DisplayName("같은 대화에 중복 피드백을 생성할 수 없다")
        fun `given existing feedback, when createFeedback again, then throws exception`() {
            // given
            val request = FeedbackCreateRequest(chatId = 1L, isPositive = true)

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(chatRepository.findById(1L)).thenReturn(Optional.of(chat))
            whenever(feedbackRepository.existsByUserIdAndChatId(1L, 1L)).thenReturn(true)

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                feedbackService.createFeedback(1L, Role.MEMBER.name, request)
            }
            assertEquals("Feedback already exists for this chat", exception.message)
        }

        @Test
        @DisplayName("존재하지 않는 대화에 피드백을 생성할 수 없다")
        fun `given non-existent chat, when createFeedback, then throws exception`() {
            // given
            val request = FeedbackCreateRequest(chatId = 999L, isPositive = true)

            whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
            whenever(chatRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                feedbackService.createFeedback(1L, Role.MEMBER.name, request)
            }
            assertEquals("Chat not found", exception.message)
        }
    }

    @Nested
    @DisplayName("updateStatus - 피드백 상태 변경")
    inner class UpdateStatusTest {

        @Test
        @DisplayName("피드백 상태를 resolved로 변경할 수 있다")
        fun `given pending feedback, when updateStatus to resolved, then succeeds`() {
            // given
            val feedback = FeedbackEntity(id = 1L, user = user, chat = chat, isPositive = true, status = FeedbackStatus.PENDING)

            whenever(feedbackRepository.findById(1L)).thenReturn(Optional.of(feedback))
            whenever(feedbackRepository.save(any<FeedbackEntity>())).thenReturn(feedback)

            // when
            val result = feedbackService.updateStatus(1L, "resolved")

            // then
            assertEquals("resolved", result.status)
        }

        @Test
        @DisplayName("대소문자 구분 없이 상태를 변경할 수 있다")
        fun `given uppercase status, when updateStatus, then succeeds`() {
            // given
            val feedback = FeedbackEntity(id = 1L, user = user, chat = chat, isPositive = true, status = FeedbackStatus.PENDING)

            whenever(feedbackRepository.findById(1L)).thenReturn(Optional.of(feedback))
            whenever(feedbackRepository.save(any<FeedbackEntity>())).thenReturn(feedback)

            // when
            val result = feedbackService.updateStatus(1L, "RESOLVED")

            // then
            assertEquals("resolved", result.status)
        }

        @Test
        @DisplayName("잘못된 상태값이면 예외를 던진다")
        fun `given invalid status, when updateStatus, then throws exception`() {
            // given
            val feedback = FeedbackEntity(id = 1L, user = user, chat = chat, isPositive = true)

            whenever(feedbackRepository.findById(1L)).thenReturn(Optional.of(feedback))

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                feedbackService.updateStatus(1L, "invalid")
            }
            assertTrue(exception.message!!.contains("Invalid status"))
        }

        @Test
        @DisplayName("존재하지 않는 피드백이면 예외를 던진다")
        fun `given non-existent feedback, when updateStatus, then throws exception`() {
            // given
            whenever(feedbackRepository.findById(999L)).thenReturn(Optional.empty())

            // when & then
            val exception = assertThrows(IllegalArgumentException::class.java) {
                feedbackService.updateStatus(999L, "resolved")
            }
            assertEquals("Feedback not found", exception.message)
        }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.jupiter.api.Assertions.assertTrue(condition)
    }
}
