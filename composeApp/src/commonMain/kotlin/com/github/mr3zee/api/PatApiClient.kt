package com.github.mr3zee.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class PatApiClient(private val client: HttpClient) {

    suspend fun listTokens(): List<PatInfo> {
        val response = client.get(serverUrl(ApiRoutes.Pats.BASE))
        return response.body()
    }

    suspend fun createToken(name: String, expiresInDays: Int?): CreatePatResponse {
        val response = client.post(serverUrl(ApiRoutes.Pats.BASE)) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = name, expiresInDays = expiresInDays))
        }
        return response.body()
    }

    suspend fun revokeToken(id: String) {
        client.delete(serverUrl(ApiRoutes.Pats.byId(id)))
    }

    // Admin endpoints
    suspend fun listUserTokens(userId: String): List<PatInfo> {
        val response = client.get(serverUrl(ApiRoutes.Pats.forUser(userId)))
        return response.body()
    }

    suspend fun revokeUserToken(userId: String, tokenId: String) {
        client.delete(serverUrl(ApiRoutes.Pats.forUserById(userId, tokenId)))
    }
}
