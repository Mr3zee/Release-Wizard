@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.api

import kotlinx.rpc.annotations.Rpc
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Rpc
interface TeamCityConnectionService {
    // TeamCity connection management
    suspend fun createTeamCityConnection(request: CreateTeamCityConnectionRequest): ConnectionResponse
    suspend fun updateConnection(request: UpdateConnectionRequest): ConnectionResponse
    suspend fun deleteConnection(connectionId: Uuid): SuccessResponse
    suspend fun listConnections(): ConnectionListResponse
    suspend fun getConnection(connectionId: Uuid): ConnectionResponse

    // Testing
    suspend fun testConnection(connectionId: Uuid): ConnectionTestResponse

    // TeamCity-specific operations
    suspend fun getTeamCityProjects(connectionId: Uuid): TeamCityProjectListResponse
    suspend fun getTeamCityBuildConfigurations(connectionId: Uuid, projectId: String): TeamCityBuildConfigListResponse
}
