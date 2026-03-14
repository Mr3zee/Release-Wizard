package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class ApprovalRule(
    val requiredRole: UserRole? = null,
    val requiredUserIds: List<String> = emptyList(),
    val requiredCount: Int = 1,
)

@Serializable
data class BlockApproval(
    val userId: String,
    val username: String,
    val approvedAt: Long,
)
