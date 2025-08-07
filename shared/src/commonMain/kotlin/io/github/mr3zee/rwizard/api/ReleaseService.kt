package io.github.mr3zee.rwizard.api

import io.github.mr3zee.rwizard.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface ReleaseService {
    
    // Release lifecycle management
    suspend fun createRelease(request: CreateReleaseRequest): ReleaseResponse
    suspend fun getRelease(releaseId: UUID): ReleaseResponse
    suspend fun listReleases(request: ListReleasesRequest = ListReleasesRequest()): ReleaseListResponse
    
    suspend fun startRelease(releaseId: UUID): ReleaseResponse
    suspend fun pauseRelease(releaseId: UUID): ReleaseResponse
    suspend fun cancelRelease(releaseId: UUID): ReleaseResponse
    suspend fun deleteRelease(releaseId: UUID): SuccessResponse
    
    // Block execution management
    suspend fun restartBlock(blockExecutionId: UUID, parameters: Map<String, String> = emptyMap()): BlockExecutionResponse
    suspend fun pauseBlock(blockExecutionId: UUID): BlockExecutionResponse
    suspend fun cancelBlock(blockExecutionId: UUID): BlockExecutionResponse
    
    // Real-time updates
    fun subscribeToReleaseUpdates(releaseId: UUID): Flow<ReleaseUpdate>
    fun subscribeToBlockUpdates(blockExecutionId: UUID): Flow<BlockExecutionUpdate>
    
    // Logs and monitoring
    suspend fun getBlockLogs(blockExecutionId: UUID, request: LogRequest = LogRequest()): LogResponse
    fun streamBlockLogs(blockExecutionId: UUID): Flow<ExecutionLog>
    
    // User input handling
    suspend fun getPendingUserInputs(releaseId: UUID): UserInputListResponse
    suspend fun submitUserInput(inputId: UUID, value: String): UserInputResponse
    
    // Release analytics
    suspend fun getReleaseStatistics(projectId: UUID, request: StatisticsRequest = StatisticsRequest()): StatisticsResponse
}

// Request DTOs
@Serializable
data class CreateReleaseRequest(
    val projectId: UUID,
    val name: String,
    val description: String,
    val parameterValues: Map<String, String>
)

@Serializable
data class ListReleasesRequest(
    val projectId: UUID? = null,
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
    val fromTimestamp: kotlinx.datetime.Instant? = null,
    val toTimestamp: kotlinx.datetime.Instant? = null
)

@Serializable
data class StatisticsRequest(
    val fromDate: kotlinx.datetime.Instant? = null,
    val toDate: kotlinx.datetime.Instant? = null,
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
    abstract val releaseId: UUID
    abstract val timestamp: kotlinx.datetime.Instant
    
    @Serializable
    data class StatusUpdate(
        override val releaseId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val oldStatus: ReleaseStatus,
        val newStatus: ReleaseStatus
    ) : ReleaseUpdate()
    
    @Serializable
    data class BlockUpdate(
        override val releaseId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val blockExecution: BlockExecution
    ) : ReleaseUpdate()
    
    @Serializable
    data class LogUpdate(
        override val releaseId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val log: ExecutionLog
    ) : ReleaseUpdate()
    
    @Serializable
    data class UserInputRequired(
        override val releaseId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val userInput: UserInput
    ) : ReleaseUpdate()
}

@Serializable
sealed class BlockExecutionUpdate {
    abstract val blockExecutionId: UUID
    abstract val timestamp: kotlinx.datetime.Instant
    
    @Serializable
    data class StatusUpdate(
        override val blockExecutionId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val oldStatus: BlockExecutionStatus,
        val newStatus: BlockExecutionStatus
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class LogUpdate(
        override val blockExecutionId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val log: ExecutionLog
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class MetadataUpdate(
        override val blockExecutionId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val metadata: Map<String, String>
    ) : BlockExecutionUpdate()
    
    @Serializable
    data class OutputUpdate(
        override val blockExecutionId: UUID,
        override val timestamp: kotlinx.datetime.Instant,
        val outputs: Map<String, String>
    ) : BlockExecutionUpdate()
}
