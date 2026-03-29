package com.example.threadedchatservice.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "chats")
class ChatEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    val thread: ThreadEntity,
    @Column(columnDefinition = "TEXT", nullable = false)
    val question: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    val answer: String,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
