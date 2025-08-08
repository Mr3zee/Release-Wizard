@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package io.github.mr3zee.rwizard.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
sealed class Connection {
    abstract val id: Uuid
    abstract val name: String
    abstract val description: String
    abstract val createdAt: Instant
    abstract val updatedAt: Instant
    
    @Serializable
    data class Slack(
        override val id: Uuid,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val workspaceId: String,
        val workspaceName: String,
        val isActive: Boolean = true
    ) : Connection()
    
    @Serializable
    data class TeamCity(
        override val id: Uuid,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val serverUrl: String,
        val isActive: Boolean = true
    ) : Connection()
    
    @Serializable
    data class GitHub(
        override val id: Uuid,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val username: String,
        val isActive: Boolean = true
    ) : Connection()
    
    @Serializable
    data class MavenCentralPortal(
        override val id: Uuid,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val username: String,
        val isActive: Boolean = true
    ) : Connection()
}

@Serializable
data class ConnectionCredentials(
    val connectionId: Uuid,
    val type: ConnectionType,
    val encryptedData: String, // Encrypted JSON containing sensitive data
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
enum class ConnectionType {
    SLACK,
    TEAMCITY,
    GITHUB,
    MAVEN_CENTRAL_PORTAL
}

// Data classes for credentials (before encryption)
@Serializable
sealed class Credentials {
    @Serializable
    data class SlackCredentials(
        val botToken: String,
        val appToken: String? = null
    ) : Credentials()
    
    @Serializable
    data class TeamCityCredentials(
        val username: String,
        val password: String
    ) : Credentials()
    
    @Serializable
    data class GitHubCredentials(
        val token: String
    ) : Credentials()
    
    @Serializable
    data class MavenCentralPortalCredentials(
        val username: String,
        val password: String
    ) : Credentials()
}

@Serializable
data class MessageTemplate(
    val id: Uuid,
    val projectId: Uuid,
    val name: String,
    val description: String,
    val template: String, // Template with placeholders like {{parameter_name}}
    val createdAt: Instant,
    val updatedAt: Instant
)
