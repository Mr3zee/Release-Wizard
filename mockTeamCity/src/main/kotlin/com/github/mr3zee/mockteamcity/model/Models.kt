package com.github.mr3zee.mockteamcity.model

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
data class MockServerConfig(
    val port: Int = 8111,
    val acceptedToken: String = "",
)

@Serializable
data class RequestLogEntry(
    val method: String,
    val path: String,
    val timestamp: String,
    val statusCode: Int,
)
