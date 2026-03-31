package com.github.mr3zee.api

import kotlinx.serialization.Serializable

@Serializable
data class CreatePatRequest(
    val name: String,
    val expiresInDays: Int? = null,
)

@Serializable
data class CreatePatResponse(
    val id: String,
    val name: String,
    val token: String,
    val expiresAt: Long?,
    val createdAt: Long,
) {
    override fun toString() = "CreatePatResponse(id=$id, name=$name, token=****, expiresAt=$expiresAt, createdAt=$createdAt)"
}

@Serializable
data class PatInfo(
    val id: String,
    val name: String,
    val expiresAt: Long?,
    val lastUsedAt: Long?,
    val createdAt: Long,
    val revoked: Boolean,
)
