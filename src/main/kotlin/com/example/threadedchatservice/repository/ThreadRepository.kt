package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.ThreadEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ThreadRepository : JpaRepository<ThreadEntity, Long> {

    fun findByUserId(userId: Long): List<ThreadEntity>

    @Query("""
        SELECT t FROM ThreadEntity t
        WHERE t.user.id = :userId
        ORDER BY t.createdAt DESC
        LIMIT 1
    """)
    fun findLatestByUserId(userId: Long): ThreadEntity?
}
