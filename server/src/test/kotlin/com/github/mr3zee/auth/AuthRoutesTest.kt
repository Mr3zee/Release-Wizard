package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {

    private val testUsername = "admin"
    private val testPassword = "adminpass"

    private suspend fun io.ktor.client.HttpClient.register(
        username: String = testUsername,
        password: String = testPassword,
    ) = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(username = username, password = password))
    }

    private suspend fun io.ktor.client.HttpClient.login(
        username: String = testUsername,
        password: String = testPassword,
    ) = post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username = username, password = password))
    }

    @Test
    fun `login with valid credentials returns user info`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()

        val response = client.login()
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals(testUsername, userInfo.username)
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()

        val response = client.login(password = "wrong")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me endpoint returns user info after login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()
        client.login()

        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val userInfo = meResponse.body<UserInfo>()
        assertEquals(testUsername, userInfo.username)
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

        client.register()
        client.login()

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

        client.register()
        client.login()

        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // --- Phase 2: Multi-user Auth tests ---

    @Test
    fun `register first user gets ADMIN role`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.register(username = "first", password = "password1234")
        assertEquals(HttpStatusCode.Created, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals(UserRole.ADMIN, userInfo.role)
    }

    @Test
    fun `register second user gets USER role`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register(username = "first", password = "password1234")

        val response = client.register(username = "second", password = "password5678")
        assertEquals(HttpStatusCode.Created, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals(UserRole.USER, userInfo.role)
    }

    @Test
    fun `register duplicate username returns generic error`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register(username = "dupuser", password = "password1234")

        val response = client.register(username = "dupuser", password = "password5678")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("REGISTRATION_FAILED", error.code)
    }

    @Test
    fun `register with short password returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.register(username = "shortpw", password = "abc")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    @Test
    fun `register with blank username returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.register(username = "", password = "password1234")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    @Test
    fun `me endpoint returns role after login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register(username = "roleuser", password = "password1234")
        client.login(username = "roleuser", password = "password1234")

        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val userInfo = meResponse.body<UserInfo>()
        assertEquals(UserRole.ADMIN, userInfo.role)
        assertEquals("roleuser", userInfo.username)
    }

    @Test
    fun `user management list requires admin`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        val userClient = jsonClient()

        // First user becomes admin
        adminClient.register(username = "admin1", password = "password1234")

        // Second user is a regular user
        userClient.register(username = "user2", password = "password5678")
        userClient.login(username = "user2", password = "password5678")

        val response = userClient.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("FORBIDDEN", error.code)
    }

    @Test
    fun `admin can list users`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()

        adminClient.register(username = "admin1", password = "password1234")
        adminClient.login(username = "admin1", password = "password1234")

        val response = adminClient.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.OK, response.status)
        val userList = response.body<UserListResponse>()
        assertTrue(userList.users.any { it.username == "admin1" })
    }

    @Test
    fun `cannot demote last admin`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()

        val registerResponse = adminClient.register(username = "onlyadmin", password = "password1234")
        val adminInfo = registerResponse.body<UserInfo>()
        adminClient.login(username = "onlyadmin", password = "password1234")

        val response = adminClient.put(ApiRoutes.Auth.userRole(adminInfo.id ?: error("Admin user ID should not be null after registration"))) {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRoleRequest(role = UserRole.USER))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("LAST_ADMIN", error.code)
    }

    @Test
    fun `ownership filtering - user sees only own projects`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        val userClient = jsonClient()

        // First user becomes admin and creates a team
        adminClient.register(username = "admin1", password = "password1234")
        adminClient.login(username = "admin1", password = "password1234")
        val teamId = adminClient.loginAndCreateTeam(username = "admin1", password = "password1234")

        // Admin creates a project
        adminClient.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Admin Project", teamId = teamId))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }

        // Second user registers and logs in
        userClient.register(username = "user2", password = "password5678")
        userClient.login(username = "user2", password = "password5678")

        // user2 lists projects — should see none (only admin's project exists)
        val response = userClient.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val projectList = response.body<ProjectListResponse>()
        assertTrue(projectList.projects.isEmpty(), "Regular user should not see admin's projects")
    }
}
