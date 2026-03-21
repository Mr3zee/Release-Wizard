package com.github.mr3zee.model

import com.github.mr3zee.api.OAuthProvider
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class UserId(val value: String)

@Serializable
data class User(
    val id: UserId,
    val username: String,
    val role: UserRole,
    val createdAt: Long? = null,
    val hasPassword: Boolean = true,
    val oauthProviders: List<OAuthProvider> = emptyList(),
    val approved: Boolean = true,
)
