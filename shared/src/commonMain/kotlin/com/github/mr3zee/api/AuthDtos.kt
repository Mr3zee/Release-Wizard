package com.github.mr3zee.api

import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
)

@Serializable
data class UserInfo(
    val username: String,
    val id: String? = null,
    val role: UserRole? = null,
)

@Serializable
data class UpdateUserRoleRequest(
    val role: UserRole,
)

@Serializable
data class UserListResponse(
    val users: List<User>,
)
