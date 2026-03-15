package com.github.mr3zee.api

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ProjectLockInfo(
    val userId: String,
    val username: String,
    val acquiredAt: Instant,
    val expiresAt: Instant,
)

@Serializable
data class ProjectLockConflictResponse(
    val error: String,
    val code: String,
    val lock: ProjectLockInfo,
)
