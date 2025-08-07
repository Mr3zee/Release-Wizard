package io.github.mr3zee.rwizard.services

import io.github.mr3zee.rwizard.api.*
import io.github.mr3zee.rwizard.database.*
import io.github.mr3zee.rwizard.domain.model.User
import io.github.mr3zee.rwizard.domain.model.UUID
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Duration.Companion.hours

class AuthServiceImpl : AuthService {
    
    override suspend fun login(request: LoginRequest): AuthResponse {
        return try {
            transaction {
                val userRow = Users.leftJoin(io.github.mr3zee.rwizard.database.UserCredentials)
                    .selectAll()
                    .where { (Users.username eq request.username) and (Users.isActive eq true) }
                    .singleOrNull()
                    
                if (userRow == null) {
                    return@transaction AuthResponse(
                        success = false,
                        error = "Invalid username or password"
                    )
                }
                
                val storedHash = userRow[UserCredentials.passwordHash]
                if (!BCrypt.checkpw(request.password, storedHash)) {
                    return@transaction AuthResponse(
                        success = false,
                        error = "Invalid username or password"
                    )
                }
                
                // Create session
                val sessionId = java.util.UUID.randomUUID()
                val token = generateToken()
                val refreshToken = generateToken()
                val now = Clock.System.now()
                val expiresAt = now.plus(24.hours)
                
                UserSessions.insert {
                    it[id] = sessionId
                    it[userId] = userRow[Users.id]
                    it[this.token] = token
                    it[this.refreshToken] = refreshToken
                    it[this.expiresAt] = expiresAt
                    it[createdAt] = now
                    it[lastUsedAt] = now
                }
                
                // Update last login
                Users.update({ Users.id eq userRow[Users.id] }) {
                    it[lastLoginAt] = now
                }
                
                val user = User(
                    id = UUID(userRow[Users.id].toString()),
                    username = userRow[Users.username],
                    email = userRow[Users.email],
                    displayName = userRow[Users.displayName],
                    role = userRow[Users.role],
                    isActive = userRow[Users.isActive],
                    createdAt = userRow[Users.createdAt],
                    updatedAt = userRow[Users.updatedAt],
                    lastLoginAt = now
                )
                
                AuthResponse(
                    success = true,
                    user = user,
                    token = token,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt
                )
            }
        } catch (e: Exception) {
            AuthResponse(
                success = false,
                error = "Login failed: ${e.message}"
            )
        }
    }
    
    override suspend fun refreshToken(refreshToken: String): AuthResponse {
        return try {
            transaction {
                val sessionRow = UserSessions.leftJoin(Users)
                    .selectAll()
                    .where {
                        (UserSessions.refreshToken eq refreshToken) and
                        (UserSessions.isRevoked eq false) and
                        (UserSessions.expiresAt greater Clock.System.now())
                    }
                    .singleOrNull()
                    
                if (sessionRow == null) {
                    return@transaction AuthResponse(
                        success = false,
                        error = "Invalid refresh token"
                    )
                }
                
                // Generate new tokens
                val newToken = generateToken()
                val newRefreshToken = generateToken()
                val now = Clock.System.now()
                val expiresAt = now.plus(24.hours)
                
                UserSessions.update({ UserSessions.id eq sessionRow[UserSessions.id] }) {
                    it[token] = newToken
                    it[UserSessions.refreshToken] = newRefreshToken
                    it[UserSessions.expiresAt] = expiresAt
                    it[lastUsedAt] = now
                }
                
                val user = User(
                    id = UUID(sessionRow[Users.id].toString()),
                    username = sessionRow[Users.username],
                    email = sessionRow[Users.email],
                    displayName = sessionRow[Users.displayName],
                    role = sessionRow[Users.role],
                    isActive = sessionRow[Users.isActive],
                    createdAt = sessionRow[Users.createdAt],
                    updatedAt = sessionRow[Users.updatedAt],
                    lastLoginAt = sessionRow[Users.lastLoginAt]
                )
                
                AuthResponse(
                    success = true,
                    user = user,
                    token = newToken,
                    refreshToken = newRefreshToken,
                    expiresAt = expiresAt
                )
            }
        } catch (e: Exception) {
            AuthResponse(
                success = false,
                error = "Token refresh failed: ${e.message}"
            )
        }
    }
    
    override suspend fun logout(token: String): SuccessResponse {
        return try {
            transaction {
                UserSessions.update({ UserSessions.token eq token }) {
                    it[isRevoked] = true
                }
            }
            SuccessResponse(success = true, message = "Logged out successfully")
        } catch (e: Exception) {
            SuccessResponse(success = false, error = "Logout failed: ${e.message}")
        }
    }
    
    override suspend fun validateToken(token: String): TokenValidationResponse {
        return try {
            transaction {
                val sessionRow = UserSessions.leftJoin(Users)
                    .selectAll()
                    .where {
                        (UserSessions.token eq token) and
                        (UserSessions.isRevoked eq false) and
                        (UserSessions.expiresAt greater Clock.System.now())
                    }
                    .singleOrNull()
                    
                if (sessionRow == null) {
                    return@transaction TokenValidationResponse(isValid = false)
                }
                
                val user = User(
                    id = UUID(sessionRow[Users.id].toString()),
                    username = sessionRow[Users.username],
                    email = sessionRow[Users.email],
                    displayName = sessionRow[Users.displayName],
                    role = sessionRow[Users.role],
                    isActive = sessionRow[Users.isActive],
                    createdAt = sessionRow[Users.createdAt],
                    updatedAt = sessionRow[Users.updatedAt],
                    lastLoginAt = sessionRow[Users.lastLoginAt]
                )
                
                TokenValidationResponse(
                    isValid = true,
                    user = user,
                    expiresAt = sessionRow[UserSessions.expiresAt]
                )
            }
        } catch (e: Exception) {
            TokenValidationResponse(isValid = false)
        }
    }
    
    // Placeholder implementations for user management
    override suspend fun createUser(request: CreateUserRequest): UserResponse {
        return UserResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun updateUser(request: UpdateUserRequest): UserResponse {
        return UserResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun deleteUser(userId: UUID): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun listUsers(request: ListUsersRequest): UserListResponse {
        return UserListResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun getUser(userId: UUID): UserResponse {
        return UserResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun createApiKey(request: CreateApiKeyRequest): ApiKeyResponse {
        return ApiKeyResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun listApiKeys(): ApiKeyListResponse {
        return ApiKeyListResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun revokeApiKey(keyId: UUID): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun grantPermission(request: GrantPermissionRequest): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun revokePermission(request: RevokePermissionRequest): SuccessResponse {
        return SuccessResponse(success = false, error = "Not implemented")
    }
    
    override suspend fun getUserPermissions(userId: UUID): UserPermissionListResponse {
        return UserPermissionListResponse(success = false, error = "Not implemented")
    }
    
    private fun generateToken(): String {
        return java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID().toString()
    }
}
