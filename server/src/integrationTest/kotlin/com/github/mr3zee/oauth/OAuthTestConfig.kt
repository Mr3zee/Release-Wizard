package com.github.mr3zee.oauth

import com.github.mr3zee.TestPropertiesLoader

/**
 * Configuration for Google OAuth integration tests.
 * Loaded from local.properties or environment variables.
 *
 * Required: Google OAuth client credentials (same as server uses).
 * The test exercises the full OAuth flow by hitting Google's real endpoints.
 */
data class OAuthTestConfig(
    val googleClientId: String,
    val googleClientSecret: String,
) {
    companion object {
        fun loadOrNull(): OAuthTestConfig? {
            val props = TestPropertiesLoader.loadProperties()
            if (props != null) {
                val clientId = props.getProperty("google.oauth.clientId")
                val clientSecret = props.getProperty("google.oauth.clientSecret")
                if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
                    return OAuthTestConfig(
                        googleClientId = clientId,
                        googleClientSecret = clientSecret,
                    )
                }
            }

            val clientId = System.getenv("GOOGLE_OAUTH_CLIENT_ID")
            val clientSecret = System.getenv("GOOGLE_OAUTH_CLIENT_SECRET")
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                return null
            }

            return OAuthTestConfig(
                googleClientId = clientId,
                googleClientSecret = clientSecret,
            )
        }
    }
}
