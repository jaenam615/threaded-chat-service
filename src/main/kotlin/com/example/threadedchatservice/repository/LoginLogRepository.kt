package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.LoginLogEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface LoginLogRepository : JpaRepository<LoginLogEntity, Long> {
    fun countByCreatedAtAfter(since: OffsetDateTime): Long
}
