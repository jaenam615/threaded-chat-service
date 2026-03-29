package com.example.threadedchatservice.service.admin

import com.example.threadedchatservice.dto.response.ActivityResponse
import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.LoginLogRepository
import com.example.threadedchatservice.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AdminService(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val loginLogRepository: LoginLogRepository,
) {

    fun getActivity(): ActivityResponse {
        val since = LocalDateTime.now().minusDays(1)

        // 24시간 내 가입자 수
        val signupCount = userRepository.countByCreatedAtAfter(since)

        val loginCount = loginLogRepository.countByCreatedAtAfter(since)

        // 24시간 내 채팅 수
        val chatCount = chatRepository.countSince(since)

        return ActivityResponse(
            signupCount = signupCount,
            loginCount = loginCount,
            chatCount = chatCount,
        )
    }

    fun generateReport(): String {
        val since = LocalDateTime.now().minusDays(1)

        val sb = StringBuilder()
        sb.appendLine("user_id,user_email,user_name,chat_id,question,answer,created_at")

        val chats = chatRepository.findAllSince(since)

        chats.forEach { chat ->
            val user = chat.thread.user
            sb.appendLine(
                "${user.id},${user.email},${user.name},${chat.id}," +
                "\"${chat.question.replace("\"", "\"\"")}\",\"${chat.answer.replace("\"", "\"\"")}\",${chat.createdAt}"
            )
        }

        return sb.toString()
    }
}
