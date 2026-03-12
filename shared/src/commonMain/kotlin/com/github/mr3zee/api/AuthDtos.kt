package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class UserInfo(
    val username: String,
)
