package com.github.mr3zee.api

import com.github.mr3zee.model.ClientType
import com.github.mr3zee.model.User
import com.github.mr3zee.util.RuntimeContext
import com.github.mr3zee.util.currentRuntimeContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApiClient(private val client: HttpClient) {

    private val clientType: ClientType
        get() = when (currentRuntimeContext()) {
            RuntimeContext.DESKTOP -> ClientType.DESKTOP
            RuntimeContext.BROWSER -> ClientType.BROWSER
        }

    suspend fun login(username: String, password: String): UserInfo {
        val response = client.post(serverUrl(ApiRoutes.Auth.LOGIN)) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password, clientType = clientType))
        }
        return response.body()
    }

    suspend fun register(username: String, password: String): UserInfo {
        val response = client.post(serverUrl(ApiRoutes.Auth.REGISTER)) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = username, password = password, clientType = clientType))
        }
        return response.body()
    }

    suspend fun logout() {
        client.post(serverUrl(ApiRoutes.Auth.LOGOUT))
    }

    suspend fun me(): UserInfo? {
        return try {
            val response = client.get(serverUrl(ApiRoutes.Auth.ME))
            response.body()
        } catch (_: Exception) {
            null
        }
    }

    fun googleOAuthBrowserUrl(): String = serverUrl(ApiRoutes.Auth.OAuth.GOOGLE)

    suspend fun changeUsername(newUsername: String, currentPassword: String?): UserInfo {
        val response = client.put(serverUrl(ApiRoutes.Auth.CHANGE_USERNAME)) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = newUsername, currentPassword = currentPassword))
        }
        return response.body()
    }

    suspend fun changePassword(currentPassword: String?, newPassword: String) {
        client.put(serverUrl(ApiRoutes.Auth.CHANGE_PASSWORD)) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = currentPassword, newPassword = newPassword))
        }
    }

    suspend fun deleteAccount(confirmUsername: String, currentPassword: String?) {
        client.delete(serverUrl(ApiRoutes.Auth.DELETE_ACCOUNT)) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = confirmUsername, currentPassword = currentPassword))
        }
    }

    suspend fun generatePasswordResetLink(userId: String): PasswordResetLinkResponse {
        val response = client.post(serverUrl(ApiRoutes.Auth.GENERATE_PASSWORD_RESET)) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        return response.body()
    }

    suspend fun validateResetToken(token: String): Boolean {
        return try {
            client.post(serverUrl(ApiRoutes.Auth.VALIDATE_RESET_TOKEN)) {
                contentType(ContentType.Application.Json)
                setBody(ValidateResetTokenRequest(token = token))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resetPassword(token: String, newPassword: String) {
        client.post(serverUrl(ApiRoutes.Auth.RESET_PASSWORD)) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = token, newPassword = newPassword))
        }
    }

    suspend fun getUsers(): List<User> {
        val response = client.get(serverUrl(ApiRoutes.Auth.USERS))
        val body: UserListResponse = response.body()
        return body.users
    }

    suspend fun getPasswordPolicy(): PasswordPolicyResponse {
        val response = client.get(serverUrl(ApiRoutes.Auth.PASSWORD_POLICY))
        return response.body()
    }
}
