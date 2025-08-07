package io.github.mr3zee.rwizard.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class Connection {
    abstract val id: UUID
    abstract val name: String
    abstract val description: String
    abstract val createdAt: Instant
    abstract val updatedAt: Instant
    
    @Serializable
    data class Slack(
        override val id: UUID,
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
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val serverUrl: String,
        val isActive: Boolean = true
    ) : Connection()
    
    @Serializable
    data class GitHub(
        override val id: UUID,
        override val name: String,
        override val description: String,
        override val createdAt: Instant,
        override val updatedAt: Instant,
        val username: String,
        val isActive: Boolean = true
    ) : Connection()
    
    @Serializable
    data class MavenCentralPortal(
        override val id: UUID,
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
    val connectionId: UUID,
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
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val description: String,
    val template: String, // Template with placeholders like {{parameter_name}}
    val createdAt: Instant,
    val updatedAt: Instant
)
