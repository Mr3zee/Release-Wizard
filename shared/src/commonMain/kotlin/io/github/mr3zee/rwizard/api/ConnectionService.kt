package io.github.mr3zee.rwizard.api

import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface ConnectionService {
    
    // Connection management
    suspend fun createSlackConnection(request: CreateSlackConnectionRequest): ConnectionResponse
    suspend fun createTeamCityConnection(request: CreateTeamCityConnectionRequest): ConnectionResponse
    suspend fun createGitHubConnection(request: CreateGitHubConnectionRequest): ConnectionResponse
    suspend fun createMavenCentralConnection(request: CreateMavenCentralConnectionRequest): ConnectionResponse
    
    suspend fun updateConnection(request: UpdateConnectionRequest): ConnectionResponse
    suspend fun deleteConnection(connectionId: UUID): SuccessResponse
    suspend fun listConnections(type: ConnectionType? = null): ConnectionListResponse
    suspend fun getConnection(connectionId: UUID): ConnectionResponse
    
    // Connection testing
    suspend fun testConnection(connectionId: UUID): ConnectionTestResponse
    
    // Connection-specific operations
    suspend fun getSlackChannels(connectionId: UUID): SlackChannelListResponse
    suspend fun getTeamCityProjects(connectionId: UUID): TeamCityProjectListResponse
    suspend fun getTeamCityBuildConfigurations(connectionId: UUID, projectId: String): TeamCityBuildConfigListResponse
    suspend fun getGitHubRepositories(connectionId: UUID): GitHubRepositoryListResponse
    suspend fun getGitHubWorkflows(connectionId: UUID, repository: String): GitHubWorkflowListResponse
}

@Rpc
interface AuthService {
    
    // Authentication
    suspend fun login(request: LoginRequest): AuthResponse
    suspend fun refreshToken(refreshToken: String): AuthResponse
    suspend fun logout(token: String): SuccessResponse
    suspend fun validateToken(token: String): TokenValidationResponse
    
    // User management (admin only)
    suspend fun createUser(request: CreateUserRequest): UserResponse
    suspend fun updateUser(request: UpdateUserRequest): UserResponse
    suspend fun deleteUser(userId: UUID): SuccessResponse
    suspend fun listUsers(request: ListUsersRequest = ListUsersRequest()): UserListResponse
    suspend fun getUser(userId: UUID): UserResponse
    
    // API Key management
    suspend fun createApiKey(request: CreateApiKeyRequest): ApiKeyResponse
    suspend fun listApiKeys(): ApiKeyListResponse
    suspend fun revokeApiKey(keyId: UUID): SuccessResponse
    
    // Permission management
    suspend fun grantPermission(request: GrantPermissionRequest): SuccessResponse
    suspend fun revokePermission(request: RevokePermissionRequest): SuccessResponse
    suspend fun getUserPermissions(userId: UUID): UserPermissionListResponse
}

// Connection Request DTOs
@Serializable
data class CreateSlackConnectionRequest(
    val name: String,
    val description: String,
    val workspaceId: String,
    val workspaceName: String,
    val credentials: Credentials.SlackCredentials
)

@Serializable
data class CreateTeamCityConnectionRequest(
    val name: String,
    val description: String,
    val serverUrl: String,
    val credentials: Credentials.TeamCityCredentials
)

@Serializable
data class CreateGitHubConnectionRequest(
    val name: String,
    val description: String,
    val username: String,
    val credentials: Credentials.GitHubCredentials
)

@Serializable
data class CreateMavenCentralConnectionRequest(
    val name: String,
    val description: String,
    val username: String,
    val credentials: Credentials.MavenCentralPortalCredentials
)

@Serializable
data class UpdateConnectionRequest(
    val connectionId: UUID,
    val name: String? = null,
    val description: String? = null,
    val credentials: Credentials? = null
)

// Auth Request DTOs
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val displayName: String,
    val password: String,
    val role: UserRole
)

@Serializable
data class UpdateUserRequest(
    val userId: UUID,
    val email: String? = null,
    val displayName: String? = null,
    val role: UserRole? = null,
    val isActive: Boolean? = null
)

@Serializable
data class ListUsersRequest(
    val search: String? = null,
    val role: UserRole? = null,
    val isActive: Boolean? = null,
    val limit: Int = 50,
    val offset: Int = 0
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
    val permissions: List<Permission>,
    val projectIds: List<UUID> = emptyList(),
    val expiresAt: kotlinx.datetime.Instant? = null
)

@Serializable
data class GrantPermissionRequest(
    val userId: UUID,
    val permission: Permission,
    val projectId: UUID? = null
)

@Serializable
data class RevokePermissionRequest(
    val userId: UUID,
    val permission: Permission,
    val projectId: UUID? = null
)

// Response DTOs
@Serializable
data class ConnectionResponse(
    val success: Boolean,
    val connection: Connection? = null,
    val error: String? = null
)

@Serializable
data class ConnectionListResponse(
    val success: Boolean,
    val connections: List<Connection> = emptyList(),
    val error: String? = null
)

@Serializable
data class ConnectionTestResponse(
    val success: Boolean,
    val isValid: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val user: User? = null,
    val token: String? = null,
    val refreshToken: String? = null,
    val expiresAt: kotlinx.datetime.Instant? = null,
    val error: String? = null
)

@Serializable
data class TokenValidationResponse(
    val isValid: Boolean,
    val user: User? = null,
    val expiresAt: kotlinx.datetime.Instant? = null
)

@Serializable
data class UserResponse(
    val success: Boolean,
    val user: User? = null,
    val error: String? = null
)

@Serializable
data class UserListResponse(
    val success: Boolean,
    val users: List<User> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class ApiKeyResponse(
    val success: Boolean,
    val apiKey: ApiKey? = null,
    val plainTextKey: String? = null, // Only returned on creation
    val error: String? = null
)

@Serializable
data class ApiKeyListResponse(
    val success: Boolean,
    val apiKeys: List<ApiKey> = emptyList(),
    val error: String? = null
)

@Serializable
data class UserPermissionListResponse(
    val success: Boolean,
    val permissions: List<UserPermission> = emptyList(),
    val error: String? = null
)

// Service-specific response DTOs
@Serializable
data class SlackChannelListResponse(
    val success: Boolean,
    val channels: List<SlackChannel> = emptyList(),
    val error: String? = null
)

@Serializable
data class SlackChannel(
    val id: String,
    val name: String,
    val isPrivate: Boolean
)

@Serializable
data class TeamCityProjectListResponse(
    val success: Boolean,
    val projects: List<TeamCityProject> = emptyList(),
    val error: String? = null
)

@Serializable
data class TeamCityProject(
    val id: String,
    val name: String,
    val description: String,
    val href: String
)

@Serializable
data class TeamCityBuildConfigListResponse(
    val success: Boolean,
    val buildConfigs: List<TeamCityBuildConfig> = emptyList(),
    val error: String? = null
)

@Serializable
data class TeamCityBuildConfig(
    val id: String,
    val name: String,
    val projectId: String,
    val href: String
)

@Serializable
data class GitHubRepositoryListResponse(
    val success: Boolean,
    val repositories: List<GitHubRepository> = emptyList(),
    val error: String? = null
)

@Serializable
data class GitHubRepository(
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String
)

@Serializable
data class GitHubWorkflowListResponse(
    val success: Boolean,
    val workflows: List<GitHubWorkflow> = emptyList(),
    val error: String? = null
)

@Serializable
data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)
