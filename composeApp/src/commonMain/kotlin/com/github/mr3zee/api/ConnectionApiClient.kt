package com.github.mr3zee.api

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.parameter
import io.ktor.http.*

class ConnectionApiClient(private val client: HttpClient) {

    suspend fun listConnections(
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: String? = null,
    ): ConnectionListResponse {
        val response = client.get(serverUrl(ApiRoutes.Connections.BASE)) {
            parameter("offset", offset)
            parameter("limit", limit)
            search?.let { parameter("q", it) }
            type?.let { parameter("type", it) }
        }
        return response.body()
    }

    suspend fun getConnection(id: ConnectionId): Connection {
        val response = client.get(serverUrl(ApiRoutes.Connections.byId(id.value)))
        return response.body<ConnectionResponse>().connection
    }

    suspend fun createConnection(request: CreateConnectionRequest): Connection {
        val response = client.post(serverUrl(ApiRoutes.Connections.BASE)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body<ConnectionResponse>().connection
    }

    suspend fun updateConnection(id: ConnectionId, request: UpdateConnectionRequest): Connection {
        val response = client.put(serverUrl(ApiRoutes.Connections.byId(id.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body<ConnectionResponse>().connection
    }

    suspend fun deleteConnection(id: ConnectionId) {
        client.delete(serverUrl(ApiRoutes.Connections.byId(id.value)))
    }

    suspend fun testConnection(id: ConnectionId): ConnectionTestResult {
        val response = client.post(serverUrl(ApiRoutes.Connections.test(id.value)))
        return response.body()
    }
}
