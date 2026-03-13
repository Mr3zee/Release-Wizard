package com.github.mr3zee

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.api.LoginRequest
import com.github.mr3zee.plugins.HealthStatus
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthAndErrorTest {

    @Test
    fun `health check returns UP without auth`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.HEALTH)
        assertEquals(HttpStatusCode.OK, response.status)
        val health = response.body<HealthStatus>()
        assertEquals("UP", health.status)
    }

    @Test
    fun `401 returns JSON ErrorResponse`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("UNAUTHORIZED", error.code)
        assertEquals("Not authenticated", error.error)
        assertNotNull(error.correlationId)
    }

    @Test
    fun `400 returns JSON ErrorResponse with correlationId`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // Login first
        client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }

        val response = client.get(ApiRoutes.Releases.byId("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("BAD_REQUEST", error.code)
        assertNotNull(error.correlationId)
    }

    @Test
    fun `404 returns JSON ErrorResponse`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }

        val response = client.get(ApiRoutes.Releases.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("NOT_FOUND", error.code)
        assertNotNull(error.correlationId)
    }

    @Test
    fun `login 401 returns INVALID_CREDENTIALS code`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "wrong"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INVALID_CREDENTIALS", error.code)
    }

    @Test
    fun `correlation ID is returned in response header`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.HEALTH)
        val correlationId = response.headers["X-Correlation-Id"]
        assertNotNull(correlationId)
        // Should be a valid UUID format
        kotlin.runCatching { java.util.UUID.fromString(correlationId) }
            .onFailure { throw AssertionError("Correlation ID is not a valid UUID: $correlationId") }
    }
}
