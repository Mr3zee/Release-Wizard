package com.github.mr3zee.api

import com.github.mr3zee.model.AuditEvent
import com.github.mr3zee.model.JoinRequest
import com.github.mr3zee.model.Team
import com.github.mr3zee.model.TeamInvite
import com.github.mr3zee.model.TeamMembership
import com.github.mr3zee.model.TeamRole
import kotlinx.serialization.Serializable

// Team CRUD

@Serializable
data class CreateTeamRequest(
    val name: String,
    val description: String = "",
)

@Serializable
data class UpdateTeamRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class TeamResponse(
    val team: Team,
    val memberCount: Int = 0,
)

@Serializable
data class TeamDetailResponse(
    val team: Team,
    val members: List<TeamMembership>,
)

@Serializable
data class TeamListResponse(
    val teams: List<TeamResponse>,
    val pagination: PaginationInfo? = null,
)

// Membership

@Serializable
data class UpdateMemberRoleRequest(
    val role: TeamRole,
)

@Serializable
data class TeamMemberListResponse(
    val members: List<TeamMembership>,
)

// Invites

@Serializable
data class CreateInviteRequest(
    val username: String,
)

@Serializable
data class InviteListResponse(
    val invites: List<TeamInvite>,
)

// Join Requests

@Serializable
data class JoinRequestListResponse(
    val requests: List<JoinRequest>,
)

// Audit

@Serializable
data class AuditEventListResponse(
    val events: List<AuditEvent>,
    val pagination: PaginationInfo? = null,
)
