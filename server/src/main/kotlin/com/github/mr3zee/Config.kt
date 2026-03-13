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

/**
 * Read a config property with optional env var override.
 * Env var takes precedence if set; otherwise falls back to YAML value.
 */
private fun ApplicationConfig.propertyOrEnv(path: String, envVar: String): String {
    return System.getenv(envVar)?.takeIf { it.isNotEmpty() }
        ?: property(path).getString()
}

fun ApplicationConfig.databaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = propertyOrEnv("app.database.url", "DB_URL"),
        user = propertyOrEnv("app.database.user", "DB_USER"),
        password = propertyOrEnv("app.database.password", "DB_PASSWORD"),
        driver = property("app.database.driver").getString(),
    )
}

fun ApplicationConfig.authConfig(): AuthConfig {
    return AuthConfig(
        username = propertyOrEnv("app.auth.username", "AUTH_USERNAME"),
        password = propertyOrEnv("app.auth.password", "AUTH_PASSWORD"),
        sessionSignKey = propertyOrEnv("app.auth.sessionSignKey", "AUTH_SESSION_SIGN_KEY"),
    )
}

fun ApplicationConfig.encryptionConfig(): EncryptionConfig {
    return EncryptionConfig(
        key = propertyOrEnv("app.encryption.key", "ENCRYPTION_KEY"),
    )
}

fun ApplicationConfig.webhookConfig(): WebhookConfig {
    return WebhookConfig(
        baseUrl = propertyOrEnv("app.webhook.baseUrl", "WEBHOOK_BASE_URL"),
    )
}
