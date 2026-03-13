package com.github.mr3zee

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String,
)

data class AuthConfig(
    val username: String,
    val password: String,
    val sessionSignKey: String,
)

data class EncryptionConfig(
    val key: String,
)

data class WebhookConfig(
    val baseUrl: String,
)

fun ApplicationConfig.databaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = property("app.database.url").getString(),
        user = property("app.database.user").getString(),
        password = property("app.database.password").getString(),
        driver = property("app.database.driver").getString(),
    )
}

fun ApplicationConfig.authConfig(): AuthConfig {
    return AuthConfig(
        username = property("app.auth.username").getString(),
        password = property("app.auth.password").getString(),
        sessionSignKey = property("app.auth.sessionSignKey").getString(),
    )
}

fun ApplicationConfig.encryptionConfig(): EncryptionConfig {
    return EncryptionConfig(
        key = property("app.encryption.key").getString(),
    )
}

fun ApplicationConfig.webhookConfig(): WebhookConfig {
    return WebhookConfig(
        baseUrl = property("app.webhook.baseUrl").getString(),
    )
}
