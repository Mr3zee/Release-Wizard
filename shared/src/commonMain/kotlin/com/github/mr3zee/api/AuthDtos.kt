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
    val createdAt: Long? = null,
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

@Serializable
data class ChangeUsernameRequest(
    val newUsername: String,
    val currentPassword: String,
) {
    override fun toString() = "ChangeUsernameRequest(newUsername=$newUsername, password=****)"
}

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
) {
    override fun toString() = "ChangePasswordRequest(password=****)"
}

@Serializable
data class DeleteAccountRequest(
    val confirmUsername: String,
    val currentPassword: String,
) {
    override fun toString() = "DeleteAccountRequest(confirmUsername=$confirmUsername, password=****)"
}

@Serializable
data class GeneratePasswordResetRequest(
    val userId: String,
)

@Serializable
data class PasswordResetLinkResponse(
    val token: String,
    val resetUrl: String,
    val expiresAt: Long,
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
) {
    override fun toString() = "ResetPasswordRequest(token=****, password=****)"
}

@Serializable
data class ValidateResetTokenRequest(
    val token: String,
) {
    override fun toString() = "ValidateResetTokenRequest(token=****)"
}
