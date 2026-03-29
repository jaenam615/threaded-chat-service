package com.example.threadedchatservice.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "threads")
class ThreadEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)
