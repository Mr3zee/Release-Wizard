package com.github.mr3zee.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApiClient(private val client: HttpClient) {

    suspend fun login(username: String, password: String): UserInfo {
        val response = client.post(serverUrl(ApiRoutes.Auth.LOGIN)) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password))
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
}
