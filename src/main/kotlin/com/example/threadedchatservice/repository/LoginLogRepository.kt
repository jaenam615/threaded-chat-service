package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.LoginLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface LoginLogRepository : JpaRepository<LoginLogEntity, Long> {
    fun countByCreatedAtAfter(since: LocalDateTime): Long
}
