package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.ThreadEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ThreadRepository : JpaRepository<ThreadEntity, Long> {
    fun findByUserId(userId: Long): List<ThreadEntity>

    fun findByUserId(
        userId: Long,
        pageable: Pageable,
    ): Page<ThreadEntity>

    @Query(
        """
        SELECT t FROM ThreadEntity t
        WHERE t.user.id = :userId
        ORDER BY t.createdAt DESC
        LIMIT 1
    """,
    )
    fun findLatestByUserId(userId: Long): ThreadEntity?
}
