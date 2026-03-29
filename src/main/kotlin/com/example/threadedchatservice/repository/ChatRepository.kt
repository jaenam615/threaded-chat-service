package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.ChatEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface ChatRepository : JpaRepository<ChatEntity, Long> {
    fun findByThreadId(threadId: Long): List<ChatEntity>

    fun findByThreadIdOrderByCreatedAtAsc(threadId: Long): List<ChatEntity>

    @Query("SELECT c FROM ChatEntity c WHERE c.thread.id IN :threadIds ORDER BY c.createdAt ASC")
    fun findByThreadIdInOrderByCreatedAtAsc(threadIds: List<Long>): List<ChatEntity>

    @Query(
        """
        SELECT c FROM ChatEntity c
        WHERE c.thread.id = :threadId
        ORDER BY c.createdAt DESC
        LIMIT 1
    """,
    )
    fun findLatestByThreadId(threadId: Long): ChatEntity?

    @Query("SELECT COUNT(c) FROM ChatEntity c WHERE c.createdAt >= :since")
    fun countSince(since: OffsetDateTime): Long

    @Query("SELECT c FROM ChatEntity c JOIN FETCH c.thread t JOIN FETCH t.user WHERE c.createdAt >= :since ORDER BY c.createdAt ASC")
    fun findAllSince(since: OffsetDateTime): List<ChatEntity>
}
