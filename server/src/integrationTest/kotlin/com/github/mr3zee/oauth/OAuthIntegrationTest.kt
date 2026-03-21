@file:Suppress("FunctionName")

package com.github.mr3zee.oauth

import com.github.mr3zee.AppJson
import com.github.mr3zee.OAuthConfig
import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.ClientType
import com.github.mr3zee.testAuthConfig
import com.github.mr3zee.testDbConfig
import com.github.mr3zee.testEncryptionConfig
import com.github.mr3zee.testModule
import com.github.mr3zee.testPasswordPolicyConfig
import com.github.mr3zee.testWebhookConfig
import com.github.mr3zee.appModule
import com.github.mr3zee.auth.*
import com.github.mr3zee.audit.auditModule
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.mavenpublication.mavenTriggerModule
import com.github.mr3zee.notifications.notificationsModule
import com.github.mr3zee.plugins.*
import com.github.mr3zee.projects.LockConflictException
import com.github.mr3zee.projects.projectLockModule
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releasesModule
import com.github.mr3zee.schedules.schedulesModule
import com.github.mr3zee.tags.tagsModule
import com.github.mr3zee.teams.teamsModule
import com.github.mr3zee.triggers.triggersModule
import com.github.mr3zee.webhooks.webhooksModule
import com.github.mr3zee.configureRouting
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

/**
 * Integration tests for Google OAuth that exercise the real Ktor OAuth plugin
 * with actual Google credentials. Requires GOOGLE_OAUTH_CLIENT_ID and
 * GOOGLE_OAUTH_CLIENT_SECRET to be set (via env vars or local.properties).
 *
 * These tests verify:
 * - The OAuth redirect URL is correctly formed with real Google credentials
 * - The password policy endpoint reports Google as an available provider
 * - The server creates the OAuth authentication provider without errors
 */
class OAuthIntegrationTest {

    companion object {
        private var config: OAuthTestConfig? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = OAuthTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
        }
    }

    private fun oauthConfig(): OAuthConfig {
        val cfg = config ?: error("OAuthTestConfig not loaded")
        return OAuthConfig(
            googleClientId = cfg.googleClientId,
            googleClientSecret = cfg.googleClientSecret,
        )
    }

    /**
     * Test module that includes OAuth configuration.
     * Uses H2 in-memory database — no Docker/PostgreSQL needed.
     */
    private fun Application.oauthTestModule() {
        val dbConfig = testDbConfig()
        val authConfig = testAuthConfig()
        val webhookConfig = testWebhookConfig()
        val oauthCfg = oauthConfig()

        val executionScope = CoroutineScope(SupervisorJob(coroutineContext.job) + Dispatchers.Default)

        install(Koin) {
            slf4jLogger()
            allowOverride(true)
            modules(
                appModule(dbConfig, testEncryptionConfig(), authConfig, webhookConfig, testPasswordPolicyConfig(), oauthCfg),
                auditModule,
                authModule,
                projectsModule,
                projectLockModule,
                connectionsModule,
                webhooksModule,
                releasesModule,
                notificationsModule,
                schedulesModule,
                triggersModule,
                mavenTriggerModule,
                tagsModule,
                teamsModule,
                module { single { executionScope } },
                module {
                    single<BlockExecutor> { StubBlockExecutor() }
                },
            )
        }

        install(DefaultHeaders) {
            header("X-Content-Type-Options", "nosniff")
        }
        install(RequestSizeLimit)
        install(RateLimit) {
            register(RateLimitName("login")) {
                rateLimiter(limit = 100, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("authenticated-api")) {
                rateLimiter(limit = 200, refillPeriod = 60.seconds)
                requestKey { call -> call.sessions.get<UserSession>()?.userId ?: call.request.local.remoteHost }
            }
            register(RateLimitName("webhook")) {
                rateLimiter(limit = 30, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("invite")) {
                rateLimiter(limit = 10, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("create-team")) {
                rateLimiter(limit = 10, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("create-project")) {
                rateLimiter(limit = 10, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("test-connection")) {
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
            register(RateLimitName("password-reset")) {
                rateLimiter(limit = 5, refillPeriod = 60.seconds)
                requestKey { call -> call.request.local.remoteHost }
            }
        }
        install(CorrelationId)
        install(Sessions) {
            cookie<UserSession>("SESSION") {
                cookie.path = "/"
                cookie.maxAgeInSeconds = maxOf(authConfig.browserSessionTtlSeconds, authConfig.desktopSessionTtlSeconds)
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "Lax"
                if (authConfig.sessionEncryptKey.isNotEmpty()) {
                    transform(SessionTransportTransformerEncrypt(hex(authConfig.sessionEncryptKey), hex(authConfig.sessionSignKey)))
                } else {
                    transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
                }
            }
        }

        // Install OAuth authentication provider with real Google credentials
        val resolvedOAuthConfig = getKoin().get<OAuthConfig>()
        val resolvedHttpClient = getKoin().get<io.ktor.client.HttpClient>()
        val resolvedWebhookConfig = getKoin().get<com.github.mr3zee.WebhookConfig>()

        install(Authentication) {
            session<UserSession>("session-auth") {
                validate { it }
                challenge {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse(error = "Not authenticated", code = "UNAUTHORIZED"),
                    )
                }
            }
            if (resolvedOAuthConfig.isGoogleConfigured) {
                oauth("auth-oauth-google") {
                    urlProvider = {
                        "${resolvedWebhookConfig.baseUrl.trimEnd('/')}/api/v1/auth/oauth/google/callback"
                    }
                    providerLookup = {
                        OAuthServerSettings.OAuth2ServerSettings(
                            name = "google",
                            authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
                            accessTokenUrl = "https://oauth2.googleapis.com/token",
                            clientId = resolvedOAuthConfig.googleClientId
                                ?: error("Google OAuth client ID not configured"),
                            clientSecret = resolvedOAuthConfig.googleClientSecret
                                ?: error("Google OAuth client secret not configured"),
                            defaultScopes = listOf("openid", "email", "profile"),
                            requestMethod = HttpMethod.Post,
                        )
                    }
                    client = resolvedHttpClient
                }
            }
        }
        install(SessionTtl)
        install(CsrfProtection)
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
        }
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.application.environment.log.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = "Internal server error", code = "INTERNAL_ERROR"),
                )
            }
        }
        configureRouting()
    }

    private fun ApplicationTestBuilder.oauthClient() = createClient {
        install(ClientContentNegotiation) {
            json(AppJson)
        }
        install(HttpCookies)
        followRedirects = false // Don't follow redirects — we want to inspect them
    }

    @Test
    fun `OAuth redirect goes to Google with correct client_id`() = testApplication {
        application { oauthTestModule() }
        val client = oauthClient()

        val response = client.get(ApiRoutes.Auth.OAuth.GOOGLE)
        assertEquals(HttpStatusCode.Found, response.status)

        val location = response.headers[HttpHeaders.Location]
        assertNotNull(location, "OAuth redirect should have Location header")
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth"), "Should redirect to Google")

        val cfg = config ?: error("Config not loaded")
        assertTrue(location.contains("client_id=${cfg.googleClientId}"), "Should include client_id")
        assertTrue(location.contains("scope=openid+email+profile") || location.contains("scope=openid%20email%20profile"),
            "Should include scopes")
        assertTrue(location.contains("redirect_uri="), "Should include redirect_uri")
        assertTrue(location.contains("response_type=code"), "Should include response_type=code")
        assertTrue(location.contains("state="), "Should include state parameter")
    }

    @Test
    fun `password policy reports Google as available provider`() = testApplication {
        application { oauthTestModule() }
        val client = createClient {
            install(ClientContentNegotiation) { json(AppJson) }
        }

        val response = client.get(ApiRoutes.Auth.PASSWORD_POLICY)
        assertEquals(HttpStatusCode.OK, response.status)

        val policy: PasswordPolicyResponse = response.body()
        assertTrue(policy.oauthProviders.contains(OAuthProvider.GOOGLE),
            "Password policy should report GOOGLE as available")
    }

    @Test
    fun `OAuth callback without valid code redirects with error`() = testApplication {
        application { oauthTestModule() }
        val client = oauthClient()

        // Call callback without going through the redirect flow (no valid state/code)
        val response = client.get("${ApiRoutes.Auth.OAuth.GOOGLE_CALLBACK}?error=access_denied")
        // Ktor's OAuth plugin should handle this via fallback or error handling
        // The response should be a redirect (not a 500)
        assertTrue(response.status.value in 200..399,
            "Callback with error should not cause 500, got ${response.status}")
    }
}
