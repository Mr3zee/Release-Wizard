package com.github.mr3zee.model

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
)
