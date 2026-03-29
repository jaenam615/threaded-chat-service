package com.example.threadedchatservice.service.auth

import com.example.threadedchatservice.config.JwtUtil
import com.example.threadedchatservice.dto.request.LoginRequest
import com.example.threadedchatservice.dto.request.SignupRequest
import com.example.threadedchatservice.dto.response.AuthResponse
import com.example.threadedchatservice.entity.LoginLogEntity
import com.example.threadedchatservice.entity.Role
import com.example.threadedchatservice.entity.UserEntity
import com.example.threadedchatservice.repository.LoginLogRepository
import com.example.threadedchatservice.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val loginLogRepository: LoginLogRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
) {
    fun signup(request: SignupRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user =
            userRepository.save(
                UserEntity(
                    email = request.email,
                    password = passwordEncoder.encode(request.password),
                    name = request.name,
                ),
            )

        val token = jwtUtil.generateToken(user.id, user.email, user.role.name)

        return AuthResponse(
            token = token,
            userId = user.id,
            email = user.email,
            name = user.name,
            role = user.role.name.lowercase(),
        )
    }

    fun createAdmin(request: SignupRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already exists")
        }

        val user =
            userRepository.save(
                UserEntity(
                    email = request.email,
                    password = passwordEncoder.encode(request.password),
                    name = request.name,
                    role = Role.ADMIN,
                ),
            )

        val token = jwtUtil.generateToken(user.id, user.email, user.role.name)

        return AuthResponse(
            token = token,
            userId = user.id,
            email = user.email,
            name = user.name,
            role = user.role.name.lowercase(),
        )
    }

    fun login(request: LoginRequest): AuthResponse {
        val user =
            userRepository.findByEmail(request.email)
                ?: throw IllegalArgumentException("Invalid email or password")

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        loginLogRepository.save(LoginLogEntity(user = user))

        val token = jwtUtil.generateToken(user.id, user.email, user.role.name)

        return AuthResponse(
            token = token,
            userId = user.id,
            email = user.email,
            name = user.name,
            role = user.role.name.lowercase(),
        )
    }
}
