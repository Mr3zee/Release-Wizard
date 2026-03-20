package com.github.mr3zee

import io.ktor.server.config.*

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String,
    val maxPoolSize: Int = 10,
)

data class AuthConfig(
    val sessionSignKey: String,
    val sessionEncryptKey: String = "",
    val sessionTtlSeconds: Long = 86400,
    val sessionRefreshThresholdSeconds: Long = 60,
    val absoluteSessionLifetimeSeconds: Long = 604800,
)

data class PasswordPolicyConfig(
    val minLength: Int = 12,
    val requireUppercase: Boolean = true,
    val requireDigit: Boolean = true,
    val requireSpecial: Boolean = false,
)

data class EncryptionConfig(
    val key: String,
)

data class WebhookConfig(
    val baseUrl: String,
)

data class CorsConfig(
    val allowedOrigins: List<String>,
)

fun ApplicationConfig.databaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = property("app.database.url").getString(),
        user = property("app.database.user").getString(),
        password = property("app.database.password").getString(),
        driver = property("app.database.driver").getString(),
        maxPoolSize = propertyOrNull("app.database.maxPoolSize")?.getString()?.toIntOrNull() ?: 10,
    )
}

fun ApplicationConfig.authConfig(): AuthConfig {
    val sessionSignKey = property("app.auth.sessionSignKey").getString()

    require(sessionSignKey.length >= 64) {
        "app.auth.sessionSignKey must be at least 64 hex characters (32 bytes). " +
            "Set AUTH_SESSION_SIGN_KEY env var."
    }

    val sessionEncryptKey = propertyOrNull("app.auth.sessionEncryptKey")?.getString() ?: ""
    if (sessionEncryptKey.isNotEmpty()) {
        require(sessionEncryptKey.length == 32 || sessionEncryptKey.length == 64) {
            "app.auth.sessionEncryptKey must be 32 hex characters (16 bytes for AES-128) or " +
                "64 hex characters (32 bytes for AES-256). Set AUTH_SESSION_ENCRYPT_KEY env var."
        }
    }

    val sessionTtlSeconds = propertyOrNull("app.auth.sessionTtlSeconds")?.getString()?.toLongOrNull() ?: 86400L
    val sessionRefreshThresholdSeconds = propertyOrNull("app.auth.sessionRefreshThresholdSeconds")?.getString()?.toLongOrNull() ?: 60L
    val absoluteSessionLifetimeSeconds = propertyOrNull("app.auth.absoluteSessionLifetimeSeconds")?.getString()?.toLongOrNull() ?: 604800L

    return AuthConfig(
        sessionSignKey = sessionSignKey,
        sessionEncryptKey = sessionEncryptKey,
        sessionTtlSeconds = sessionTtlSeconds,
        sessionRefreshThresholdSeconds = sessionRefreshThresholdSeconds,
        absoluteSessionLifetimeSeconds = absoluteSessionLifetimeSeconds,
    )
}

fun ApplicationConfig.passwordPolicyConfig(): PasswordPolicyConfig {
    return PasswordPolicyConfig(
        minLength = propertyOrNull("app.auth.password.minLength")?.getString()?.toIntOrNull() ?: 12,
        requireUppercase = propertyOrNull("app.auth.password.requireUppercase")?.getString()?.toBooleanStrictOrNull() ?: true,
        requireDigit = propertyOrNull("app.auth.password.requireDigit")?.getString()?.toBooleanStrictOrNull() ?: true,
        requireSpecial = propertyOrNull("app.auth.password.requireSpecial")?.getString()?.toBooleanStrictOrNull() ?: false,
    )
}

fun ApplicationConfig.encryptionConfig(): EncryptionConfig {
    val key = property("app.encryption.key").getString()

    // CONN-H1: Decode Base64 at config load time and validate decoded key is exactly 32 bytes (256-bit AES)
    val keyBytes = try {
        java.util.Base64.getDecoder().decode(key)
    } catch (_: IllegalArgumentException) {
        error("app.encryption.key must be a valid Base64-encoded string. Set ENCRYPTION_KEY env var.")
    }
    require(keyBytes.size == 32) {
        "app.encryption.key must decode to exactly 32 bytes (256-bit AES key). " +
            "Got ${keyBytes.size} bytes. Set ENCRYPTION_KEY env var."
    }

    return EncryptionConfig(key = key)
}

fun ApplicationConfig.webhookConfig(): WebhookConfig {
    return WebhookConfig(
        baseUrl = property("app.webhook.baseUrl").getString(),
    )
}

fun ApplicationConfig.corsConfig(): CorsConfig {
    val origins = propertyOrNull("app.cors.allowedOrigins")?.getList() ?: emptyList()
    return CorsConfig(allowedOrigins = origins)
}
