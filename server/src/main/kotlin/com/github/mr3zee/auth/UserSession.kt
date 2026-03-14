package com.github.mr3zee.auth

import com.github.mr3zee.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val username: String,
    val userId: String,
    val role: UserRole,
    val csrfToken: String = "",
    val createdAt: Long = 0L,
    val lastAccessedAt: Long = 0L,
)
