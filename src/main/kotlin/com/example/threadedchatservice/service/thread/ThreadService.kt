package com.example.threadedchatservice.service.thread

import com.example.threadedchatservice.repository.ChatRepository
import com.example.threadedchatservice.repository.ThreadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ThreadService(
    private val threadRepository: ThreadRepository,
    private val chatRepository: ChatRepository,
) {

    @Transactional
    fun deleteThread(userId: Long, role: String, threadId: Long) {
        val thread = threadRepository.findById(threadId)
            .orElseThrow { IllegalArgumentException("Thread not found") }

        // 권한 체크: 본인 것만 삭제 가능 (admin은 모두 가능)
        if (role != "admin" && thread.user.id != userId) {
            throw IllegalArgumentException("Not authorized to delete this thread")
        }

        // 관련 채팅 삭제
        val chats = chatRepository.findByThreadId(threadId)
        chatRepository.deleteAll(chats)

        // 스레드 삭제
        threadRepository.delete(thread)
    }
}
