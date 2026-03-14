package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class TeamMembership(
    val teamId: TeamId,
    val userId: UserId,
    val username: String,
    val role: TeamRole,
    val joinedAt: Long = 0,
)
