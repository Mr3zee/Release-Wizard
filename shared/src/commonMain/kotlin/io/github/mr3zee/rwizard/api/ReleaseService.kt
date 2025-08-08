@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.api

import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Rpc
interface ReleaseService {
    
    // Release lifecycle management
    suspend fun createRelease(request: CreateReleaseRequest): ReleaseResponse
    suspend fun getRelease(releaseId: Uuid): ReleaseResponse
    suspend fun listReleases(request: ListReleasesRequest = ListReleasesRequest()): ReleaseListResponse
    
    suspend fun startRelease(releaseId: Uuid): ReleaseResponse
    suspend fun pauseRelease(releaseId: Uuid): ReleaseResponse
    suspend fun cancelRelease(releaseId: Uuid): ReleaseResponse
    suspend fun deleteRelease(releaseId: Uuid): SuccessResponse
    
    // Block execution management
    suspend fun restartBlock(blockExecutionId: Uuid, parameters: Map<String, String> = emptyMap()): BlockExecutionResponse
    suspend fun pauseBlock(blockExecutionId: Uuid): BlockExecutionResponse
    suspend fun cancelBlock(blockExecutionId: Uuid): BlockExecutionResponse
    
    // Real-time updates
    fun subscribeToReleaseUpdates(releaseId: Uuid): Flow<ReleaseUpdate>
    fun subscribeToBlockUpdates(blockExecutionId: Uuid): Flow<BlockExecutionUpdate>
    
    // Logs and monitoring
    suspend fun getBlockLogs(blockExecutionId: Uuid, request: LogRequest = LogRequest()): LogResponse
    fun streamBlockLogs(blockExecutionId: Uuid): Flow<ExecutionLog>
    
    // User input handling
    suspend fun getPendingUserInputs(releaseId: Uuid): UserInputListResponse
    suspend fun submitUserInput(inputId: Uuid, value: String): UserInputResponse
    
    // Release analytics
    suspend fun getReleaseStatistics(projectId: Uuid, request: StatisticsRequest = StatisticsRequest()): StatisticsResponse
}

// Request DTOs
@Serializable
data class CreateReleaseRequest(
    val projectId: Uuid,
    val name: String,
    val description: String,
    val parameterValues: Map<String, String>
)

@Serializable
data class ListReleasesRequest(
    val projectId: Uuid? = null,
    val status: ReleaseStatus? = null,
    val search: String? = null,
    val limit: Int = 50,
    val offset: Int = 0,
    val sortBy: ReleaseSortBy = ReleaseSortBy.CREATED_AT,
    val sortOrder: SortOrder = SortOrder.DESC
)

@Serializable
enum class ReleaseSortBy {
    NAME, CREATED_AT, STARTED_AT, COMPLETED_AT, STATUS
}

@Serializable
data class LogRequest(
    val level: LogLevel? = null,
    val source: String? = null,
    val limit: Int = 100,
    val offset: Int = 0,
    val fromTimestamp: Instant? = null,
    val toTimestamp: Instant? = null
)

@Serializable
data class StatisticsRequest(
    val fromDate: Instant? = null,
    val toDate: Instant? = null,
    val groupBy: StatisticsGroupBy = StatisticsGroupBy.DAY
)

@Serializable
enum class StatisticsGroupBy {
    DAY, WEEK, MONTH
}

// Response DTOs
@Serializable
data class ReleaseResponse(
    val success: Boolean,
    val release: Release? = null,
    val error: String? = null
)

@Serializable
data class ReleaseListResponse(
    val success: Boolean,
    val releases: List<Release> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class BlockExecutionResponse(
    val success: Boolean,
    val blockExecution: BlockExecution? = null,
    val error: String? = null
)

@Serializable
data class LogResponse(
    val success: Boolean,
    val logs: List<ExecutionLog> = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

@Serializable
data class UserInputListResponse(
    val success: Boolean,
    val inputs: List<UserInput> = emptyList(),
    val error: String? = null
)

@Serializable
data class UserInputResponse(
    val success: Boolean,
    val input: UserInput? = null,
    val error: String? = null
)

@Serializable
data class StatisticsResponse(
    val success: Boolean,
    val statistics: ReleaseStatistics? = null,
    val error: String? = null
)

@Serializable
data class ReleaseStatistics(
    val totalReleases: Int,
    val successfulReleases: Int,
    val failedReleases: Int,
    val averageDuration: Long, // in seconds
    val releasesByDate: List<ReleaseDateCount>,
    val blockSuccessRates: List<BlockSuccessRate>,
    val mostUsedBlocks: List<BlockUsageCount>
)

@Serializable
data class ReleaseDateCount(
    val date: kotlinx.datetime.LocalDate,
    val count: Int,
    val successCount: Int,
    val failureCount: Int
)

@Serializable
data class BlockSuccessRate(
    val blockType: String,
    val totalExecutions: Int,
    val successfulExecutions: Int,
    val successRate: Double
)

@Serializable
data class BlockUsageCount(
    val blockType: String,
    val usageCount: Int,
    val averageDuration: Long
)

// Real-time update DTOs
@Serializable
sealed class ReleaseUpdate {
    abstract val releaseId: Uuid
    abstract val timestamp: Instant
    
    @Serializable
    data class StatusUpdate(
        override val releaseId: Uuid,
        override val timestamp: Instant,
        val oldStatus: ReleaseStatus,
        val newStatus: ReleaseStatus
    ) : ReleaseUpdate()
    
    @Serializable
    data class BlockUpdate(
        override val releaseId: Uuid,
        override val timestamp: Instant,
        val blockExecution: BlockExecution
    ) : ReleaseUpdate()
    
    @Serializable
    data class LogUpdate(
        override val releaseId: Uuid,
        override val timestamp: Instant,
        val log: ExecutionLog
    ) : ReleaseUpdate()
    
    @Serializable
    data class UserInputRequired(
        override val releaseId: Uuid,
        override val timestamp: Instant,
        val userInput: UserInput
    ) : ReleaseUpdate()
}

@Serializable
sealed class BlockExecutionUpdate {
    abstract val blockExecutionId: Uuid
    abstract val timestamp: Instant
    
    @Serializable
    data class StatusUpdate(
        override val blockExecutionId: Uuid,
        override val timestamp: Instant,
        val oldStatus: BlockExecutionStatus,
        val newStatus: BlockExecutionStatus
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class LogUpdate(
        override val blockExecutionId: Uuid,
        override val timestamp: Instant,
        val log: ExecutionLog
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class MetadataUpdate(
        override val blockExecutionId: Uuid,
        override val timestamp: Instant,
        val metadata: Map<String, String>
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class OutputUpdate(
        override val blockExecutionId: Uuid,
        override val timestamp: Instant,
        val outputs: Map<String, String>
    ) : BlockExecutionUpdate()
}
