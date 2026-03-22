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
    val pepperSecret: ByteArray? = null,
    val pepperSecretOld: ByteArray? = null,
)

data class PasswordPolicyConfig(
    val minLength: Int,
    val requireUppercase: Boolean,
    val requireDigit: Boolean,
    val requireSpecial: Boolean,
)

data class EncryptionConfig(
    val key: String,
)

data class WebhookConfig(
    val baseUrl: String,
)

data class OAuthConfig(
    val googleClientId: String?,
    val googleClientSecret: String?,
) {
    val isGoogleConfigured: Boolean
        get() = !googleClientId.isNullOrBlank() && !googleClientSecret.isNullOrBlank()

    override fun toString() = "OAuthConfig(googleClientId=$googleClientId, googleClientSecret=****)"
}

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

    val browserSessionTtlSeconds = propertyOrNull("app.auth.browserSessionTtlSeconds")?.getString()?.toLongOrNull() ?: 2_592_000L
    val desktopSessionTtlSeconds = propertyOrNull("app.auth.desktopSessionTtlSeconds")?.getString()?.toLongOrNull() ?: 31_536_000L
    val sessionRefreshThresholdSeconds = propertyOrNull("app.auth.sessionRefreshThresholdSeconds")?.getString()?.toLongOrNull() ?: 60L
    val browserAbsoluteLifetimeSeconds = propertyOrNull("app.auth.browserAbsoluteLifetimeSeconds")?.getString()?.toLongOrNull() ?: 7_776_000L
    val desktopAbsoluteLifetimeSeconds = propertyOrNull("app.auth.desktopAbsoluteLifetimeSeconds")?.getString()?.toLongOrNull() ?: 63_072_000L

    val pepperSecret = decodePepperKey(
        propertyOrNull("app.auth.pepper")?.getString(),
        envName = "PASSWORD_PEPPER",
    )
    val pepperSecretOld = decodePepperKey(
        propertyOrNull("app.auth.pepperOld")?.getString(),
        envName = "PASSWORD_PEPPER_OLD",
    )

    require(pepperSecretOld == null || pepperSecret != null) {
        "PASSWORD_PEPPER_OLD is set but PASSWORD_PEPPER is not. " +
            "Cannot use an old pepper without a current pepper."
    }
    if (pepperSecret != null && pepperSecretOld != null) {
        require(!pepperSecret.contentEquals(pepperSecretOld)) {
            "PASSWORD_PEPPER and PASSWORD_PEPPER_OLD must be different values."
        }
    }

    return AuthConfig(
        sessionSignKey = sessionSignKey,
        sessionEncryptKey = sessionEncryptKey,
        browserSessionTtlSeconds = browserSessionTtlSeconds,
        desktopSessionTtlSeconds = desktopSessionTtlSeconds,
        sessionRefreshThresholdSeconds = sessionRefreshThresholdSeconds,
        browserAbsoluteLifetimeSeconds = browserAbsoluteLifetimeSeconds,
        desktopAbsoluteLifetimeSeconds = desktopAbsoluteLifetimeSeconds,
        pepperSecret = pepperSecret,
        pepperSecretOld = pepperSecretOld,
    )
}

private fun decodePepperKey(raw: String?, envName: String): ByteArray? {
    if (raw.isNullOrBlank()) return null
    val bytes = try {
        java.util.Base64.getDecoder().decode(raw)
    } catch (_: IllegalArgumentException) {
        error("$envName must be a valid Base64-encoded string.")
    }
    require(bytes.size == 32) {
        "$envName must decode to exactly 32 bytes (256-bit HMAC key). Got ${bytes.size} bytes."
    }
    return bytes
}

fun ApplicationConfig.passwordPolicyConfig(): PasswordPolicyConfig {
    return PasswordPolicyConfig(
        minLength = propertyOrNull("app.auth.password.minLength")?.getString()?.toIntOrNull() ?: 16,
        requireUppercase = propertyOrNull("app.auth.password.requireUppercase")?.getString()?.toBooleanStrictOrNull() ?: true,
        requireDigit = propertyOrNull("app.auth.password.requireDigit")?.getString()?.toBooleanStrictOrNull() ?: true,
        requireSpecial = propertyOrNull("app.auth.password.requireSpecial")?.getString()?.toBooleanStrictOrNull() ?: true,
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

private val configLog = org.slf4j.LoggerFactory.getLogger("com.github.mr3zee.Config")

fun ApplicationConfig.oauthConfig(): OAuthConfig {
    val clientId = propertyOrNull("app.auth.oauth.google.clientId")?.getString()?.ifBlank { null }
    val clientSecret = propertyOrNull("app.auth.oauth.google.clientSecret")?.getString()?.ifBlank { null }

    val setFields = listOfNotNull(
        clientId?.let { "clientId" },
        clientSecret?.let { "clientSecret" },
    )
    if (setFields.isNotEmpty() && setFields.size < 2) {
        configLog.warn(
            "Partial Google OAuth config detected (only {} set). " +
                "Both clientId and clientSecret are required for OAuth to work. " +
                "Set GOOGLE_OAUTH_CLIENT_ID and GOOGLE_OAUTH_CLIENT_SECRET.",
            setFields.joinToString(),
        )
    }

    return OAuthConfig(
        googleClientId = clientId,
        googleClientSecret = clientSecret,
    )
}

fun ApplicationConfig.corsConfig(): CorsConfig {
    val origins = propertyOrNull("app.cors.allowedOrigins")?.getList()
        ?.filter { origin ->
            if (origin.isBlank()) {
                configLog.warn("Blank CORS origin filtered — verify CORS_ALLOWED_ORIGIN env vars if cross-origin access is needed")
                false
            } else true
        }
        ?: emptyList()
    return CorsConfig(allowedOrigins = origins)
}
