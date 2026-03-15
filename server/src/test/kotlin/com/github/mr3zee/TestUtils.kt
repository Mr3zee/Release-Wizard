package com.github.mr3zee

import com.github.mr3zee.api.*
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.plugins.CorrelationId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.plugins.CsrfProtection
import com.github.mr3zee.plugins.RequestSizeLimit
import com.github.mr3zee.plugins.SessionTtl
import com.github.mr3zee.projects.LockConflictException
import com.github.mr3zee.projects.projectLockModule
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releasesModule
import com.github.mr3zee.notifications.notificationsModule
import com.github.mr3zee.schedules.schedulesModule
import com.github.mr3zee.tags.tagsModule
import com.github.mr3zee.teams.teamsModule
import com.github.mr3zee.triggers.triggersModule
import com.github.mr3zee.webhooks.webhooksModule
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.StubBlockExecutor
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.serialization.SerializationException
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun testDbConfig() = DatabaseConfig(
    url = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    user = "sa",
    password = "",
    driver = "org.h2.Driver",
)

fun testAuthConfig() = AuthConfig(
    sessionSignKey = "6162636465666768696a6b6c6d6e6f707172737475767778797a313233343536",
)

fun testEncryptionConfig() = EncryptionConfig(
    key = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
)

fun testWebhookConfig() = WebhookConfig(
    baseUrl = "http://localhost:8080",
)

fun testPasswordPolicyConfig() = PasswordPolicyConfig(
    minLength = 8,
    requireUppercase = false,
    requireDigit = false,
    requireSpecial = false,
)

/**
 * Test override module:
 * - Replaces real executors with stubs (existing tests create blocks without connections)
 * - Replaces CIO HttpClient with MockEngine (connection tests and executors don't hit real APIs)
 */
val testOverrideModule = module {
    single<BlockExecutor> {
        StubBlockExecutor()
    }
    single<HttpClient> {
        createTestHttpClient()
    }
    single { CoroutineScope(SupervisorJob()) }
}

fun Application.testModuleWithPasswordPolicy(
    passwordPolicyConfig: PasswordPolicyConfig,
    dbConfig: DatabaseConfig = testDbConfig(),
) = testModule(dbConfig, passwordPolicyConfig)

fun Application.testModule(
    dbConfig: DatabaseConfig = testDbConfig(),
    passwordPolicyConfig: PasswordPolicyConfig = testPasswordPolicyConfig(),
    authConfig: AuthConfig = testAuthConfig(),
) {
    install(Koin) {
        slf4jLogger()
        allowOverride(true)
        modules(
            appModule(dbConfig, testEncryptionConfig(), authConfig, testWebhookConfig(), passwordPolicyConfig),
            authModule,
            projectsModule,
            projectLockModule,
            connectionsModule,
            webhooksModule,
            releasesModule,
            notificationsModule,
            schedulesModule,
            triggersModule,
            tagsModule,
            teamsModule,
            testOverrideModule,
        )
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }

    install(RequestSizeLimit)

    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
        }
    }

    install(CorrelationId)

    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
        }
    }
    install(Authentication) {
        session<UserSession>("session-auth") {
            validate { it }
            challenge {
                val correlationId = call.attributes.getOrNull(CorrelationIdKey)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        error = "Not authenticated",
                        code = "UNAUTHORIZED",
                        correlationId = correlationId,
                    ),
                )
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
        exception<IllegalArgumentException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Bad request",
                    code = "BAD_REQUEST",
                    correlationId = correlationId,
                ),
            )
        }
        exception<SerializationException> { call, cause ->
            call.application.environment.log.debug("Serialization error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Invalid request body",
                    code = "INVALID_BODY",
                    correlationId = correlationId,
                ),
            )
        }
        exception<ContentTransformationException> { call, cause ->
            call.application.environment.log.debug("Content transformation error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Invalid request body",
                    code = "INVALID_BODY",
                    correlationId = correlationId,
                ),
            )
        }
        exception<ForbiddenException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    error = cause.message ?: "Forbidden",
                    code = "FORBIDDEN",
                    correlationId = correlationId,
                ),
            )
        }
        exception<NotFoundException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = cause.message ?: "Not found",
                    code = "NOT_FOUND",
                    correlationId = correlationId,
                ),
            )
        }
        exception<LockConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                com.github.mr3zee.api.ProjectLockConflictResponse(
                    error = cause.message ?: "Project is locked",
                    code = "LOCK_CONFLICT",
                    lock = cause.lock,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal server error",
                    code = "INTERNAL_ERROR",
                    correlationId = correlationId,
                ),
            )
        }
    }
    configureRouting()
}

/**
 * Poll until condition is true.
 * [delayMillis] = 0 uses yield() (for unit tests with mocks on the same thread).
 * [delayMillis] > 0 uses delay() (for integration tests with real I/O).
 */
suspend fun waitUntil(
    maxAttempts: Int = 1000,
    delayMillis: Long = 0,
    condition: suspend () -> Boolean,
) {
    repeat(maxAttempts) {
        if (condition()) return
        if (delayMillis > 0) delay(delayMillis.milliseconds) else yield()
    }
    throw AssertionError("waitUntil timed out after $maxAttempts attempts")
}

/**
 * Ktor client plugin that captures X-CSRF-Token from responses and attaches it to subsequent requests.
 */
private val CsrfTokenTestPlugin = createClientPlugin("CsrfTokenTest") {
    var csrfToken = ""
    onRequest { request, _ ->
        if (csrfToken.isNotEmpty()) {
            request.header("X-CSRF-Token", csrfToken)
        }
    }
    onResponse { response ->
        response.headers["X-CSRF-Token"]?.let { csrfToken = it }
    }
}

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(AppJson)
    }
    install(HttpCookies)
    install(CsrfTokenTestPlugin)
}

/**
 * Registers and logs in a test user. First user in a fresh DB is auto-promoted to ADMIN.
 * Also creates a default team so that team-scoped operations work.
 */
suspend fun HttpClient.login(
    username: String = "admin",
    password: String = "adminpass",
) {
    // Register (idempotent — 409 on duplicate is fine)
    post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(username = username, password = password))
    }
    // Login to get a session cookie
    post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username = username, password = password))
    }
}

/**
 * Creates a test team and returns its ID. The logged-in user becomes TEAM_LEAD.
 */
suspend fun HttpClient.createTestTeam(name: String = "Test Team"): TeamId {
    val response = post(ApiRoutes.Teams.BASE) {
        contentType(ContentType.Application.Json)
        setBody(CreateTeamRequest(name = name))
    }
    return response.body<TeamResponse>().team.id
}

/**
 * Convenience: login + create team. Returns the team ID.
 */
suspend fun HttpClient.loginAndCreateTeam(
    username: String = "admin",
    password: String = "adminpass",
    teamName: String = "Test Team",
): TeamId {
    login(username, password)
    return createTestTeam(teamName)
}

/**
 * Creates a project with optional blocks and returns its ID as a String.
 * Requires the user to be logged in and a valid teamId.
 */
suspend fun HttpClient.createTestProject(
    teamId: TeamId,
    name: String = "Test Project",
    dagGraph: com.github.mr3zee.model.DagGraph? = null,
): String {
    val response = post(ApiRoutes.Projects.BASE) {
        contentType(ContentType.Application.Json)
        setBody(
            CreateProjectRequest(
                name = name,
                teamId = teamId,
                dagGraph = dagGraph ?: com.github.mr3zee.model.DagGraph(),
            )
        )
    }
    return response.body<ProjectResponse>().project.id.value
}

/**
 * Creates a project with a single action block and returns its ID.
 * Useful for tests that need a project that can start a release.
 */
suspend fun HttpClient.createTestProjectWithBlocks(
    teamId: TeamId,
    name: String = "Test Project",
): String {
    return createTestProject(
        teamId = teamId,
        name = name,
        dagGraph = com.github.mr3zee.model.DagGraph(
            blocks = listOf(
                com.github.mr3zee.model.Block.ActionBlock(
                    id = com.github.mr3zee.model.BlockId("block-a"),
                    name = "Build",
                    type = com.github.mr3zee.model.BlockType.TEAMCITY_BUILD,
                ),
            ),
        ),
    )
}
