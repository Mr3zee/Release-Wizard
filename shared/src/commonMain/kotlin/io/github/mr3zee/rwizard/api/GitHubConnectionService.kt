@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.api

import kotlinx.rpc.annotations.Rpc
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Rpc
interface GitHubConnectionService {
    // GitHub connection management
    suspend fun createGitHubConnection(request: CreateGitHubConnectionRequest): ConnectionResponse
    suspend fun updateConnection(request: UpdateConnectionRequest): ConnectionResponse
    suspend fun deleteConnection(connectionId: Uuid): SuccessResponse
    suspend fun listConnections(): ConnectionListResponse
    suspend fun getConnection(connectionId: Uuid): ConnectionResponse

    // Testing
    suspend fun testConnection(connectionId: Uuid): ConnectionTestResponse

    // GitHub-specific operations
    suspend fun getGitHubRepositories(connectionId: Uuid): GitHubRepositoryListResponse
    suspend fun getGitHubWorkflows(connectionId: Uuid, repository: String): GitHubWorkflowListResponse
}
