package io.github.mr3zee.rwizard.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val description: String,
    val parameterValues: Map<String, String>, // Project parameter values
    val status: ReleaseStatus,
    val blockExecutions: List<BlockExecution>,
    val startedBy: String, // User ID or system
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val updatedAt: Instant
)

@Serializable
enum class ReleaseStatus {
    PENDING,      // Created but not started
    RUNNING,      // In progress
    PAUSED,       // Paused (waiting for user input)
    SUCCEEDED,    // Completed successfully
    FAILED,       // Failed
    CANCELLED     // Cancelled by user
}

@Serializable
data class BlockExecution(
    val id: UUID,
    val releaseId: UUID,
    val blockId: UUID,
    val blockType: String, // Block class name for reference
    val status: BlockExecutionStatus,
    val parameterValues: Map<String, String>,
    val outputValues: Map<String, String> = emptyMap(),
    val logs: List<ExecutionLog> = emptyList(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val updatedAt: Instant,
    val metadata: Map<String, String> = emptyMap(), // Block-specific metadata
    val retryCount: Int = 0,
    val maxRetries: Int = 3
)

@Serializable
enum class BlockExecutionStatus {
    WAITING,           // Waiting for dependencies
    READY,            // Dependencies satisfied, ready to start
    RUNNING,          // Currently executing
    WAITING_FOR_INPUT, // Paused, waiting for user input
    SUCCEEDED,        // Completed successfully
    FAILED,           // Failed
    CANCELLED,        // Cancelled
    RETRYING          // Being retried after failure
}

@Serializable
data class ExecutionLog(
    val id: UUID,
    val blockExecutionId: UUID,
    val level: LogLevel,
    val message: String,
    val timestamp: Instant,
    val source: String? = null, // e.g., "teamcity", "slack", "system"
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

@Serializable
data class UserInput(
    val id: UUID,
    val blockExecutionId: UUID,
    val prompt: String,
    val inputType: UserInputType,
    val options: List<String> = emptyList(), // For choice-based inputs
    val isRequired: Boolean = true,
    val submittedValue: String? = null,
    val submittedBy: String? = null,
    val submittedAt: Instant? = null,
    val createdAt: Instant
)

@Serializable
enum class UserInputType {
    TEXT,
    CHOICE,
    CONFIRMATION,
    MULTI_CHOICE
}

// Specialized execution metadata for different block types
@Serializable
sealed class BlockExecutionMetadata {
    @Serializable
    data class TeamCityMetadata(
        val buildId: Long? = null,
        val buildTypeId: String,
        val buildNumber: String? = null,
        val buildUrl: String? = null,
        val queuedBuilds: List<QueuedBuild> = emptyList(),
        val completedBuilds: List<CompletedBuild> = emptyList()
    ) : BlockExecutionMetadata()
    
    @Serializable
    data class SlackMetadata(
        val messageId: String? = null,
        val channelId: String,
        val messageUrl: String? = null
    ) : BlockExecutionMetadata()
    
    @Serializable
    data class GitHubActionMetadata(
        val runId: Long? = null,
        val runUrl: String? = null,
        val workflowName: String,
        val conclusion: String? = null
    ) : BlockExecutionMetadata()
    
    @Serializable
    data class GitHubReleaseMetadata(
        val releaseId: Long? = null,
        val releaseUrl: String? = null,
        val tagName: String? = null,
        val isDraft: Boolean = true
    ) : BlockExecutionMetadata()
    
    @Serializable
    data class MavenCentralMetadata(
        val publicationId: String,
        val currentStatus: String,
        val statusUrl: String? = null,
        val lastChecked: Instant? = null
    ) : BlockExecutionMetadata()
}

@Serializable
data class QueuedBuild(
    val buildId: Long,
    val buildTypeId: String,
    val href: String
)

@Serializable
data class CompletedBuild(
    val buildId: Long,
    val buildTypeId: String,
    val buildNumber: String,
    val status: String,
    val statusText: String,
    val href: String,
    val webUrl: String,
    val finishDate: String?
)
