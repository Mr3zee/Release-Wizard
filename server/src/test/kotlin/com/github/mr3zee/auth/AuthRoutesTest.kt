package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthRoutesTest {

    @Test
    fun `login with valid credentials returns user info`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals("admin", userInfo.username)
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "wrong"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me endpoint returns user info after login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }

        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val userInfo = meResponse.body<UserInfo>()
        assertEquals("admin", userInfo.username)
    }

    @Test
    fun `me endpoint returns 401 without login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logout clears session`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }

        client.post(ApiRoutes.Auth.LOGOUT)

        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, meResponse.status)
    }

    @Test
    fun `protected endpoint requires authentication`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `protected endpoint accessible after login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }

        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
