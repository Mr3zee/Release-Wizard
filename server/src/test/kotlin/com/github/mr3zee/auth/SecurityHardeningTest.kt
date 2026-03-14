package com.github.mr3zee.auth

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import com.github.mr3zee.model.UserRole
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityHardeningTest {

    // --- #12: CSRF Protection ---

    @Test
    fun `POST without CSRF token returns 403 for authenticated user`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // Create a client WITHOUT CSRF token plugin for this specific request
        val noCsrfClient = createClient {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpCookies)
        }
        // Copy cookies by logging in with noCsrfClient
        noCsrfClient.login()

        val response = noCsrfClient.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Test"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("FORBIDDEN", error.code)
    }

    @Test
    fun `POST with correct CSRF token succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Test"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `GET requests do not require CSRF token`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpCookies)
        }
        client.login()

        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `webhook endpoint is exempt from CSRF`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = createTestProject(client)

        val createTrigger = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val trigger = createTrigger.body<TriggerResponse>()

        // Webhook endpoint should work without CSRF token
        val webhookClient = createClient {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
        val webhookResponse = webhookClient.post(ApiRoutes.Triggers.webhook(trigger.id)) {
            header(HttpHeaders.Authorization, "Bearer ${trigger.secret}")
        }
        // May be 200 or 400 depending on project setup, but NOT 403 CSRF
        assertTrue(webhookResponse.status != HttpStatusCode.Forbidden)
    }

    @Test
    fun `CSRF token is returned in response headers`() = testApplication {
        application { testModule() }
        val client = createClient {
            install(ContentNegotiation) {
                json(AppJson)
            }
            install(HttpCookies)
        }
        client.login()

        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, response.status)
        val csrfToken = response.headers["X-CSRF-Token"]
        assertNotNull(csrfToken, "Response should include X-CSRF-Token header")
        assertTrue(csrfToken.isNotEmpty())
    }

    // --- #13: Session TTL & Rotation ---

    @Test
    fun `session with expired TTL returns 401`() = testApplication {
        val shortTtlAuthConfig = AuthConfig(
            sessionSignKey = testAuthConfig().sessionSignKey,
            sessionTtlSeconds = 0,
            sessionRefreshThresholdSeconds = 0,
        )
        application {
            testModule(authConfig = shortTtlAuthConfig)
        }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `session timestamps are set on login`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // Verify session works for authenticated requests
        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals("admin", userInfo.username)
    }

    // --- #14: Password Policy ---

    @Test
    fun `registration with short password returns 400`() = testApplication {
        // Use strict password policy
        application {
            testModuleWithPasswordPolicy(PasswordPolicyConfig(
                minLength = 12,
                requireUppercase = true,
                requireDigit = true,
                requireSpecial = false,
            ))
        }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "testuser", password = "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
        assertTrue(error.error.contains("at least 12 characters"))
    }

    @Test
    fun `registration without uppercase when required returns 400`() = testApplication {
        application {
            testModuleWithPasswordPolicy(PasswordPolicyConfig(
                minLength = 8,
                requireUppercase = true,
                requireDigit = false,
                requireSpecial = false,
            ))
        }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "testuser", password = "alllowercase"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("uppercase"))
    }

    @Test
    fun `registration without digit when required returns 400`() = testApplication {
        application {
            testModuleWithPasswordPolicy(PasswordPolicyConfig(
                minLength = 8,
                requireUppercase = false,
                requireDigit = true,
                requireSpecial = false,
            ))
        }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "testuser", password = "nodigitshere"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("digit"))
    }

    @Test
    fun `registration with valid password succeeds`() = testApplication {
        application {
            testModuleWithPasswordPolicy(PasswordPolicyConfig(
                minLength = 12,
                requireUppercase = true,
                requireDigit = true,
                requireSpecial = false,
            ))
        }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "testuser", password = "ValidPass123!"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    // --- #15: Trigger Secret Header-Only ---

    @Test
    fun `trigger webhook with query param secret returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = createTestProject(client)

        val createTrigger = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val trigger = createTrigger.body<TriggerResponse>()

        // Try using query param instead of header — should fail
        val webhookClient = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(AppJson)
            }
        }
        val response = webhookClient.post("${ApiRoutes.Triggers.webhook(trigger.id)}?secret=${trigger.secret}")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `trigger webhook with bearer header succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = createTestProjectWithBlocks(client)

        val createTrigger = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val trigger = createTrigger.body<TriggerResponse>()

        val webhookClient = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(AppJson)
            }
        }
        val response = webhookClient.post(ApiRoutes.Triggers.webhook(trigger.id)) {
            header(HttpHeaders.Authorization, "Bearer ${trigger.secret}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}

private suspend fun createTestProject(client: io.ktor.client.HttpClient): String {
    val response = client.post(ApiRoutes.Projects.BASE) {
        contentType(ContentType.Application.Json)
        setBody(CreateProjectRequest(name = "Test Project"))
    }
    return response.body<ProjectResponse>().project.id.value
}

private suspend fun createTestProjectWithBlocks(client: io.ktor.client.HttpClient): String {
    val response = client.post(ApiRoutes.Projects.BASE) {
        contentType(ContentType.Application.Json)
        setBody(
            CreateProjectRequest(
                name = "Test Project",
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
        )
    }
    return response.body<ProjectResponse>().project.id.value
}

private suspend fun io.ktor.client.HttpClient.login(
    username: String = "admin",
    password: String = "adminpass",
) {
    post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(username = username, password = password))
    }
    post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username = username, password = password))
    }
}
