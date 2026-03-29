package com.example.threadedchatservice.repository

import com.example.threadedchatservice.entity.FeedbackEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FeedbackRepository : JpaRepository<FeedbackEntity, Long> {

    fun findByUserId(userId: Long, pageable: Pageable): Page<FeedbackEntity>

    fun findByIsPositive(isPositive: Boolean, pageable: Pageable): Page<FeedbackEntity>

    fun findByUserIdAndIsPositive(userId: Long, isPositive: Boolean, pageable: Pageable): Page<FeedbackEntity>

    fun existsByUserIdAndChatId(userId: Long, chatId: Long): Boolean

    fun deleteByChatIdIn(chatIds: List<Long>)
}
