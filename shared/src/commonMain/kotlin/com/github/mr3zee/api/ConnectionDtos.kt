package com.github.mr3zee.api

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType
import kotlinx.serialization.Serializable

@Serializable
data class CreateConnectionRequest(
    val name: String,
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
)

@Serializable
data class ConnectionListResponse(
    val connections: List<Connection>,
)

@Serializable
data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)
