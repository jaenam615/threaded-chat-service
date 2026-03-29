package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
    fun countByCreatedAtAfter(since: OffsetDateTime): Long
}
