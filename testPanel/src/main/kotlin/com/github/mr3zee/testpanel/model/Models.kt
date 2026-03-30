package com.github.mr3zee.testpanel.model

import kotlinx.serialization.Serializable

@Serializable
data class TcProject(
    val id: String,
    val name: String,
    val parentProjectId: String? = null,
)

@Serializable
data class TcBuildType(
    val id: String,
    val name: String,
    val projectId: String,
    val parameters: List<TcParameter> = emptyList(),
    val snapshotDependencyIds: List<String> = emptyList(),
    val artifactTemplates: List<TcArtifactTemplate> = emptyList(),
)

@Serializable
data class TcParameter(
    val name: String,
    val value: String,
    val own: Boolean = true,
    val typeRawValue: String = "",
)

@Serializable
data class TcBuild(
    val id: Int,
    val buildTypeId: String,
    val branchName: String? = null,
    val state: TcBuildState = TcBuildState.QUEUED,
    val status: TcBuildStatus? = null,
    val number: String? = null,
    val triggerProperties: Map<String, String> = emptyMap(),
    val startDate: String? = null,
    val finishDate: String? = null,
    val parentBuildId: Int? = null,
)

@Serializable
enum class TcBuildState { QUEUED, RUNNING, FINISHED }

@Serializable
enum class TcBuildStatus { SUCCESS, FAILURE, UNKNOWN }

@Serializable
data class TcArtifactTemplate(
    val name: String,
    val size: Long = 0,
    val children: List<TcArtifactTemplate> = emptyList(),
)

@Serializable
data class ServerConfig(
    val port: Int = 8111,
    val acceptedToken: String = "",
    val responseDelayMs: Long = 0,
)

@Serializable
data class RequestLogEntry(
    val method: String,
    val path: String,
    val timestamp: String,
    val statusCode: Int,
)

// --- Slack ---

@Serializable
data class SlackMessage(
    val id: Int,
    val text: String,
    val channel: String? = null,
    val receivedAt: String,
)

// --- GitHub ---

@Serializable
data class GhRepo(
    val owner: String,
    val repo: String,
)

@Serializable
data class GhWorkflow(
    val id: Int,
    val name: String,
    val path: String,
    val state: String = "active",
    val yamlContent: String = "",
    val inputParameters: List<TcParameter> = emptyList(),
)

@Serializable
data class GhWorkflowRun(
    val id: Int,
    val workflowPath: String,
    val repoKey: String,
    val ref: String? = null,
    val status: GhRunStatus = GhRunStatus.QUEUED,
    val conclusion: GhRunConclusion? = null,
    val createdAt: String,
    val jobs: List<GhJob> = emptyList(),
)

@Serializable
enum class GhRunStatus { QUEUED, IN_PROGRESS, COMPLETED }

@Serializable
enum class GhRunConclusion { SUCCESS, FAILURE, CANCELLED, TIMED_OUT }

@Serializable
data class GhJob(
    val id: Int,
    val name: String,
    val status: GhRunStatus = GhRunStatus.QUEUED,
    val conclusion: GhRunConclusion? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class GhRelease(
    val id: Int,
    val repoKey: String,
    val tagName: String,
    val name: String,
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val htmlUrl: String,
)

// --- Webhook Sender ---

@Serializable
data class WebhookSendRecord(
    val url: String,
    val token: String,
    val status: String,
    val description: String? = null,
    val responseCode: Int? = null,
    val sentAt: String,
)

// --- Release Trigger ---

@Serializable
data class TriggerRecord(
    val url: String,
    val responseCode: Int? = null,
    val responseBody: String? = null,
    val sentAt: String,
)
