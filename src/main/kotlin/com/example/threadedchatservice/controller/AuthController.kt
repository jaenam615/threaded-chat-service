package com.example.threadedchatservice.controller

import com.example.threadedchatservice.dto.request.LoginRequest
import com.example.threadedchatservice.dto.request.SignupRequest
import com.example.threadedchatservice.dto.response.AuthResponse
import com.example.threadedchatservice.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): AuthResponse {
        return authService.signup(request)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse {
        return authService.login(request)
    }
}
