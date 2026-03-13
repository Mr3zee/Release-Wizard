package com.github.mr3zee

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String,
)

data class AuthConfig(
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
    val sessionSignKey = property("app.auth.sessionSignKey").getString()

    require(sessionSignKey.length >= 64) {
        "app.auth.sessionSignKey must be at least 64 hex characters (32 bytes). " +
            "Set AUTH_SESSION_SIGN_KEY env var."
    }

    return AuthConfig(sessionSignKey = sessionSignKey)
}

fun ApplicationConfig.encryptionConfig(): EncryptionConfig {
    val key = property("app.encryption.key").getString()

    require(key.length >= 32) {
        "app.encryption.key must be at least 32 characters (Base64-encoded 256-bit key). " +
            "Set ENCRYPTION_KEY env var."
    }

    return EncryptionConfig(key = key)
}

fun ApplicationConfig.webhookConfig(): WebhookConfig {
    return WebhookConfig(
        baseUrl = property("app.webhook.baseUrl").getString(),
    )
}
