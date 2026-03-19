package com.github.mr3zee.api

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.TeamId
import kotlinx.serialization.Serializable

@Serializable
data class CreateConnectionRequest(
    val name: String,
    val teamId: TeamId,
    val type: ConnectionType,
    val config: ConnectionConfig,
)

@Serializable
data class UpdateConnectionRequest(
    val name: String? = null,
    val config: ConnectionConfig? = null,
)

@Serializable
data class ConnectionResponse(
    val connection: Connection,
    val webhookUrl: String? = null,
)

@Serializable
data class ConnectionListResponse(
    val connections: List<Connection>,
    val webhookUrls: Map<String, String> = emptyMap(),
    val pagination: PaginationInfo? = null,
)

@Serializable
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)

// External config discovery DTOs — generic across providers (TC build types, GH workflows, etc.)

@Serializable
data class ExternalConfig(
    val id: String,
    val name: String,
    val path: String,
)

@Serializable
data class ExternalConfigsResponse(
    val configs: List<ExternalConfig>,
)

@Serializable
data class ExternalConfigParameter(
    val name: String,
    val value: String,
    val type: String = "",
    val label: String = "",
    val description: String = "",
)

fun ExternalConfigParameter.toParameter(): Parameter = Parameter(
    key = name,
    value = value,
    label = label,
    description = description,
)

@Serializable
data class ExternalConfigParametersResponse(
    val parameters: List<ExternalConfigParameter>,
)
