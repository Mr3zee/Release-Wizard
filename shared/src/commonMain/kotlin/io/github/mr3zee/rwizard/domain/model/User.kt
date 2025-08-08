@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class User(
    val id: Uuid,
    val username: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastLoginAt: Instant? = null
)

@Serializable
enum class UserRole {
    ADMIN,    // Can manage all projects, users, and connections
    EDITOR,   // Can create and edit projects, manage releases
    VIEWER    // Can only view projects and releases
}

@Serializable
data class UserSession(
    val id: Uuid,
    val userId: Uuid,
    val token: String,
    val refreshToken: String? = null,
    val expiresAt: Instant,
    val createdAt: Instant,
    val lastUsedAt: Instant,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val isRevoked: Boolean = false
)

@Serializable
data class UserCredentials(
    val userId: Uuid,
    val passwordHash: String, // Hashed password
    val salt: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastPasswordChange: Instant? = null
)

@Serializable
data class UserPermission(
    val userId: Uuid,
    val projectId: Uuid? = null, // null means global permission
    val permission: Permission,
    val grantedBy: Uuid,
    val grantedAt: Instant
)

@Serializable
enum class Permission {
    // Global permissions
    MANAGE_USERS,
    MANAGE_CONNECTIONS,
    VIEW_ALL_PROJECTS,
    
    // Project-specific permissions
    VIEW_PROJECT,
    EDIT_PROJECT,
    DELETE_PROJECT,
    START_RELEASE,
    MANAGE_RELEASE,
    VIEW_RELEASE
}

@Serializable
data class ApiKey(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val keyHash: String, // Hashed API key
    val permissions: List<Permission>,
    val projectIds: List<Uuid> = emptyList(), // Empty means all projects (if has global perms)
    val expiresAt: Instant? = null, // null means no expiration
    val lastUsedAt: Instant? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
)
