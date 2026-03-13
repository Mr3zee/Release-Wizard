package com.github.mr3zee.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val username: String,
)
