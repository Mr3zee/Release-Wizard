@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.api

import kotlinx.rpc.annotations.Rpc
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Rpc
interface MavenConnectionService {
    // Maven Central connection management
    suspend fun createMavenCentralConnection(request: CreateMavenCentralConnectionRequest): ConnectionResponse
    suspend fun updateConnection(request: UpdateConnectionRequest): ConnectionResponse
    suspend fun deleteConnection(connectionId: Uuid): SuccessResponse
    suspend fun listConnections(): ConnectionListResponse
    suspend fun getConnection(connectionId: Uuid): ConnectionResponse

    // Testing
    suspend fun testConnection(connectionId: Uuid): ConnectionTestResponse
}
