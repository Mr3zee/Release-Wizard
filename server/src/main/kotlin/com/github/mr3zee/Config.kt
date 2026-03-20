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
    val browserSessionTtlSeconds: Long = 2_592_000, // 30 days
    val desktopSessionTtlSeconds: Long = 31_536_000, // 1 year
    val sessionRefreshThresholdSeconds: Long = 60,
    val browserAbsoluteLifetimeSeconds: Long = 7_776_000, // 90 days
    val desktopAbsoluteLifetimeSeconds: Long = 63_072_000, // 2 years
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
        // Only AES-128 (32 hex = 16 bytes) is supported. AES-256 (64 hex = 32 bytes) triggers
        // a Ktor 3.3.3 bug: the init block generates an IV using key size instead of block size.
        require(sessionEncryptKey.length == 32) {
            "app.auth.sessionEncryptKey must be 32 hex characters (16 bytes for AES-128). " +
                "Set AUTH_SESSION_ENCRYPT_KEY env var. Generate with: openssl rand -hex 16"
        }
    }

    val browserSessionTtlSeconds = propertyOrNull("app.auth.browserSessionTtlSeconds")?.getString()?.toLongOrNull() ?: 2_592_000L
    val desktopSessionTtlSeconds = propertyOrNull("app.auth.desktopSessionTtlSeconds")?.getString()?.toLongOrNull() ?: 31_536_000L
    val sessionRefreshThresholdSeconds = propertyOrNull("app.auth.sessionRefreshThresholdSeconds")?.getString()?.toLongOrNull() ?: 60L
    val browserAbsoluteLifetimeSeconds = propertyOrNull("app.auth.browserAbsoluteLifetimeSeconds")?.getString()?.toLongOrNull() ?: 7_776_000L
    val desktopAbsoluteLifetimeSeconds = propertyOrNull("app.auth.desktopAbsoluteLifetimeSeconds")?.getString()?.toLongOrNull() ?: 63_072_000L

    return AuthConfig(
        sessionSignKey = sessionSignKey,
        sessionEncryptKey = sessionEncryptKey,
        browserSessionTtlSeconds = browserSessionTtlSeconds,
        desktopSessionTtlSeconds = desktopSessionTtlSeconds,
        sessionRefreshThresholdSeconds = sessionRefreshThresholdSeconds,
        browserAbsoluteLifetimeSeconds = browserAbsoluteLifetimeSeconds,
        desktopAbsoluteLifetimeSeconds = desktopAbsoluteLifetimeSeconds,
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

private val corsLog = org.slf4j.LoggerFactory.getLogger("com.github.mr3zee.Config")

fun ApplicationConfig.corsConfig(): CorsConfig {
    val origins = propertyOrNull("app.cors.allowedOrigins")?.getList()
        ?.filter { origin ->
            if (origin.isBlank()) {
                corsLog.warn("Blank CORS origin filtered — verify CORS_ALLOWED_ORIGIN env vars if cross-origin access is needed")
                false
            } else true
        }
        ?: emptyList()
    return CorsConfig(allowedOrigins = origins)
}
