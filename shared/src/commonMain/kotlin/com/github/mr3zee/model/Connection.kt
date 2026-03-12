package com.github.mr3zee.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Connection(
    val id: ConnectionId,
    val name: String,
    val type: ConnectionType,
    val config: ConnectionConfig,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Serializable
sealed class ConnectionConfig {
    @Serializable
    @SerialName("slack")
    data class SlackConfig(
        val webhookUrl: String,
    ) : ConnectionConfig()

    @Serializable
    @SerialName("teamcity")
    data class TeamCityConfig(
        val serverUrl: String,
        val token: String,
    ) : ConnectionConfig()

    @Serializable
    @SerialName("github")
    data class GitHubConfig(
        val token: String,
        val owner: String,
        val repo: String,
    ) : ConnectionConfig()

    @Serializable
    @SerialName("maven_central")
    data class MavenCentralConfig(
        val username: String,
        val password: String,
    ) : ConnectionConfig()
}
