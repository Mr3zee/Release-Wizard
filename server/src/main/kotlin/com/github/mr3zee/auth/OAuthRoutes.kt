package com.github.mr3zee.auth

import com.github.mr3zee.OAuthConfig
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.model.ClientType
import com.github.mr3zee.api.OAuthProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("com.github.mr3zee.auth.OAuthRoutes")

fun Route.oauthRoutes() {
    val oauthConfig by inject<OAuthConfig>()
    if (!oauthConfig.isGoogleConfigured) return

    val oauthService by inject<OAuthService>()
    val httpClient by inject<HttpClient>()

    authenticate("auth-oauth-google") {
        // Login trigger — Ktor automatically redirects to Google's authorization endpoint
        get(ApiRoutes.Auth.OAuth.GOOGLE) {
            // Empty: the authenticate() block handles the redirect
        }

        // Callback — Ktor exchanges code for tokens, provides principal
        get(ApiRoutes.Auth.OAuth.GOOGLE_CALLBACK) {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
            if (principal == null) {
                log.warn("OAuth callback received without principal — redirecting with error")
                call.respondRedirect("/?error=google_auth_failed")
                return@get
            }

            // Fetch Google user profile
            val googleUser = try {
                httpClient.get("https://www.googleapis.com/oauth2/v3/userinfo") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                    }
                }.body<GoogleUserInfo>()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.error("Failed to fetch Google user info", e)
                call.respondRedirect("/?error=google_auth_failed")
                return@get
            }

            // Validate email is verified
            if (googleUser.emailVerified != true) {
                log.warn("Google OAuth rejected: email not verified for sub={}", googleUser.sub)
                call.respondRedirect("/?error=google_auth_failed")
                return@get
            }

            if (googleUser.sub.isBlank()) {
                log.warn("Google OAuth rejected: missing sub (user ID)")
                call.respondRedirect("/?error=google_auth_failed")
                return@get
            }

            // Find or create user — tokens are discarded after this
            val user = try {
                oauthService.findOrCreateOAuthUser(
                    provider = OAuthProvider.GOOGLE.name.lowercase(),
                    providerUserId = googleUser.sub,
                    email = googleUser.email,
                    displayName = googleUser.name,
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.error("Failed to create/find OAuth user for sub={}", googleUser.sub, e)
                call.respondRedirect("/?error=google_auth_failed")
                return@get
            }

            // Set session with CSRF token
            val now = Clock.System.now().toEpochMilliseconds()
            call.sessions.set(
                UserSession(
                    username = user.username,
                    userId = user.id.value,
                    role = user.role,
                    csrfToken = generateCsrfToken(),
                    clientType = ClientType.BROWSER,
                    createdAt = now,
                    lastAccessedAt = now,
                )
            )

            log.info("User '{}' logged in via Google OAuth", user.username)
            call.respondRedirect("/")
        }
    }
}

@Serializable
private data class GoogleUserInfo(
    val sub: String = "",
    val email: String? = null,
    @SerialName("email_verified")
    val emailVerified: Boolean? = null,
    val name: String? = null,
    val picture: String? = null,
)
