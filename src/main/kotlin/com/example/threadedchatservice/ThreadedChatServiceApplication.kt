package com.example.threadedchatservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ThreadedChatServiceApplication

fun main(args: Array<String>) {
    runApplication<ThreadedChatServiceApplication>(*args)
}
