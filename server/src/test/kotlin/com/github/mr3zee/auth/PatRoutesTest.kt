package com.github.mr3zee.auth

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatRoutesTest {

    // ── Create Token ──────────────────────────────────────────────────

    @Test
    fun `create token returns token with prefix`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "CI Token"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val pat = response.body<CreatePatResponse>()
        assertTrue(pat.token.startsWith("rwpat_"))
        assertEquals("CI Token", pat.name)
        assertNotNull(pat.id)
        assertNotNull(pat.createdAt)
    }

    @Test
    fun `create token with expiry`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Expiring", expiresInDays = 30))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val pat = response.body<CreatePatResponse>()
        assertNotNull(pat.expiresAt)
    }

    @Test
    fun `create token with blank name returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "  "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── List Tokens ──────────────────────────────────────────────────

    @Test
    fun `list tokens returns created tokens`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // Create two tokens
        client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Token A"))
        }
        client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Token B"))
        }

        val response = client.get(ApiRoutes.Pats.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val tokens = response.body<List<PatInfo>>()
        assertEquals(2, tokens.size)
    }

    // ── Revoke Token ──────────────────────────────────────────────────

    @Test
    fun `revoke token returns 204`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val createResponse = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Revokable"))
        }
        val pat = createResponse.body<CreatePatResponse>()

        val revokeResponse = client.delete(ApiRoutes.Pats.byId(pat.id))
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)

        // Verify it appears as revoked in list
        val tokens = client.get(ApiRoutes.Pats.BASE).body<List<PatInfo>>()
        val revoked = tokens.find { it.id == pat.id }
        assertNotNull(revoked)
        assertTrue(revoked.revoked)
    }

    @Test
    fun `revoke nonexistent token returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.delete(ApiRoutes.Pats.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── PAT Authentication ──────────────────────────────────────────────

    @Test
    fun `PAT authenticates API requests`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // Create a PAT
        val pat = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "API Token"))
        }.body<CreatePatResponse>()

        // Use PAT to access a protected endpoint (without cookies)
        val bearerClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
            // No HttpCookies — pure Bearer auth
        }
        val meResponse = bearerClient.get(ApiRoutes.Auth.ME) {
            header(HttpHeaders.Authorization, "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.OK, meResponse.status)
    }

    @Test
    fun `PAT request succeeds without CSRF token`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val pat = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "CSRF Test"))
        }.body<CreatePatResponse>()

        // Use PAT to make a mutating request without CSRF token
        val bearerClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
        }
        // Create another token via PAT auth (POST = mutating, no CSRF header)
        val response = bearerClient.post(ApiRoutes.Pats.BASE) {
            header(HttpHeaders.Authorization, "Bearer ${pat.token}")
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Created via PAT"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `invalid PAT returns 401`() = testApplication {
        application { testModule() }
        val bearerClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
        }
        val response = bearerClient.get(ApiRoutes.Auth.ME) {
            header(HttpHeaders.Authorization, "Bearer rwpat_invalidtokenvalue0000000000000000000000000000000000000000000")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `revoked PAT returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val pat = client.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Will Revoke"))
        }.body<CreatePatResponse>()

        // Revoke it
        client.delete(ApiRoutes.Pats.byId(pat.id))

        // Try using the revoked PAT
        val bearerClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
        }
        val response = bearerClient.get(ApiRoutes.Auth.ME) {
            header(HttpHeaders.Authorization, "Bearer ${pat.token}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── Admin Routes ──────────────────────────────────────────────────

    @Test
    fun `admin can list another user tokens`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login()

        // Create a second user
        val userClient = jsonClient()
        userClient.registerAndApproveUser(adminClient, "user2")

        // User creates a token
        userClient.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "User Token"))
        }

        // Get user2's ID
        val userMe = userClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val userId = userMe.id ?: error("Expected user ID")

        // Admin lists user2's tokens
        val response = adminClient.get(ApiRoutes.Pats.forUser(userId))
        assertEquals(HttpStatusCode.OK, response.status)
        val tokens = response.body<List<PatInfo>>()
        assertEquals(1, tokens.size)
        assertEquals("User Token", tokens[0].name)
    }

    @Test
    fun `non-admin cannot list another user tokens`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login()

        val userClient = jsonClient()
        userClient.registerAndApproveUser(adminClient, "user3")

        // Get admin's ID
        val adminMe = adminClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val adminId = adminMe.id ?: error("Expected user ID")

        // Non-admin tries to list admin's tokens
        val response = userClient.get(ApiRoutes.Pats.forUser(adminId))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `user cannot revoke another user token`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login()

        // Admin creates a token
        val pat = adminClient.post(ApiRoutes.Pats.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreatePatRequest(name = "Admin Token"))
        }.body<CreatePatResponse>()

        // Second user tries to revoke admin's token via own endpoint
        val userClient = jsonClient()
        userClient.registerAndApproveUser(adminClient, "user4")

        val response = userClient.delete(ApiRoutes.Pats.byId(pat.id))
        // Should return 404 because the token doesn't belong to user4
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
