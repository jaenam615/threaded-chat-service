package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.ChatEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ChatRepository : JpaRepository<ChatEntity, Long> {

    fun findByThreadId(threadId: Long): List<ChatEntity>

    @Query("SELECT c FROM ChatEntity c WHERE c.thread.user.id = :userId")
    fun findByUserId(userId: Long, pageable: Pageable): Page<ChatEntity>

    @Query("""
        SELECT c FROM ChatEntity c
        WHERE c.thread.id = :threadId
        ORDER BY c.createdAt DESC
        LIMIT 1
    """)
    fun findLatestByThreadId(threadId: Long): ChatEntity?

    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.createdAt >= :since")
    fun countSince(since: LocalDateTime): Long
}
