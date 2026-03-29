package com.example.threadedchatservice.client

interface LlmClient {
    fun call(prompt: String): String
}