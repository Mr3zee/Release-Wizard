package com.github.mr3zee.api

import com.github.mr3zee.model.ClientType
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val clientType: ClientType = ClientType.BROWSER,
) {
    override fun toString() = "LoginRequest(username=$username, password=****, clientType=$clientType)"
}

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val clientType: ClientType = ClientType.BROWSER,
) {
    override fun toString() = "RegisterRequest(username=$username, password=****, clientType=$clientType)"
}

@Serializable
data class UserInfo(
    val username: String,
    val id: String? = null,
    val role: UserRole? = null,
    val teams: List<UserTeamInfo> = emptyList(),
)

@Serializable
data class UserTeamInfo(
    val teamId: TeamId,
    val teamName: String,
    val role: TeamRole,
)

@Serializable
data class UpdateUserRoleRequest(
    val role: UserRole,
)

@Serializable
data class UserListResponse(
    val users: List<User>,
)
