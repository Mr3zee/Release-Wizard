package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
enum class JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

@Serializable
data class JoinRequest(
    val id: String,
    val teamId: TeamId,
    val teamName: String = "",
    val userId: UserId,
    val username: String = "",
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,
    val reviewedByUserId: UserId? = null,
    val reviewedByUsername: String? = null,
    val createdAt: Long = 0,
    val reviewedAt: Long? = null,
)
