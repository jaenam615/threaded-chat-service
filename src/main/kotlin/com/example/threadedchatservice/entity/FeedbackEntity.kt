package com.example.threadedchatservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.OffsetDateTime

@Entity
@Table(
    name = "feedbacks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "chat_id"])],
)
class FeedbackEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    val chat: ChatEntity,
    @Column(nullable = false)
    val isPositive: Boolean,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FeedbackStatus = FeedbackStatus.PENDING,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Version
    var version: Long = 0,
)
