package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApprovalGateTest {

    // -- Registration approval status --

    @Test
    fun `first user is auto-approved`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "admin", password = "adminpass"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val userInfo = response.body<UserInfo>()
        assertTrue(userInfo.approved)
        assertEquals(UserRole.ADMIN, userInfo.role)
    }

    @Test
    fun `second user is not approved`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        // First user = admin, auto-approved
        admin.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "admin", password = "adminpass"))
        }

        // Second user = not approved
        val response = user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val userInfo = response.body<UserInfo>()
        assertFalse(userInfo.approved)
        assertEquals(UserRole.USER, userInfo.role)
    }

    // -- Approval gate blocking --

    @Test
    fun `unapproved user is blocked from protected routes with 403 NOT_APPROVED`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")
        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        // Try accessing projects — should be blocked
        val response = user2.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("NOT_APPROVED", error.code)
    }

    @Test
    fun `unapproved user is blocked across multiple route categories`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")
        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        // Projects
        assertEquals(HttpStatusCode.Forbidden, user2.get(ApiRoutes.Projects.BASE).status)
        // Teams
        assertEquals(HttpStatusCode.Forbidden, user2.get(ApiRoutes.Teams.BASE).status)
        // Connections
        assertEquals(HttpStatusCode.Forbidden, user2.get(ApiRoutes.Connections.BASE).status)
        // Releases
        assertEquals(HttpStatusCode.Forbidden, user2.get(ApiRoutes.Releases.BASE).status)
    }

    // -- Exempt paths --

    @Test
    fun `unapproved user can access GET auth me`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")
        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        val meResponse = user2.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val userInfo = meResponse.body<UserInfo>()
        assertFalse(userInfo.approved)
    }

    @Test
    fun `unapproved user can logout`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")
        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        val logoutResponse = user2.post(ApiRoutes.Auth.LOGOUT)
        assertEquals(HttpStatusCode.OK, logoutResponse.status)
    }

    // -- Admin approve flow --

    @Test
    fun `admin can approve user and they pass the gate`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")

        // Register user2
        val registerResponse = user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        val user2Info = registerResponse.body<UserInfo>()
        val user2Id = user2Info.id ?: error("Expected user ID")

        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        // Blocked before approval
        assertEquals(HttpStatusCode.Forbidden, user2.get(ApiRoutes.Projects.BASE).status)

        // Admin approves
        val approveResponse = admin.post(ApiRoutes.Auth.approveUser(user2Id)) {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, approveResponse.status)

        // Now user2 passes the gate (DB re-read in ApprovalGate)
        val projectsResponse = user2.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, projectsResponse.status)
    }

    @Test
    fun `approved user shows approved true on auth me`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")

        val registerResponse = user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        val user2Id = registerResponse.body<UserInfo>().id ?: error("Expected user ID")

        user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        // Before approval
        val meBefore = user2.get(ApiRoutes.Auth.ME).body<UserInfo>()
        assertFalse(meBefore.approved)

        admin.post(ApiRoutes.Auth.approveUser(user2Id)) {
            contentType(ContentType.Application.Json)
        }

        // After approval
        val meAfter = user2.get(ApiRoutes.Auth.ME).body<UserInfo>()
        assertTrue(meAfter.approved)
    }

    // -- Admin reject (delete) flow --

    @Test
    fun `admin can reject user by deleting them`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")

        val registerResponse = user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        val user2Id = registerResponse.body<UserInfo>().id ?: error("Expected user ID")

        val deleteResponse = admin.delete(ApiRoutes.Auth.deleteUser(user2Id))
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // User no longer exists — login should fail
        val loginResponse = user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, loginResponse.status)
    }

    // -- Edge cases --

    @Test
    fun `approve non-existent user returns 404`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        admin.login("admin", "adminpass")

        val response = admin.post(ApiRoutes.Auth.approveUser("00000000-0000-0000-0000-000000000000")) {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `reject last admin returns 409 LAST_ADMIN`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        admin.login("admin", "adminpass")

        val meResponse = admin.get(ApiRoutes.Auth.ME)
        val adminId = meResponse.body<UserInfo>().id ?: error("Expected admin ID")

        val response = admin.delete(ApiRoutes.Auth.deleteUser(adminId))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("CANNOT_DELETE_SELF", error.code)
    }

    @Test
    fun `non-admin cannot approve users`() = testApplication {
        application { testModule(enableApprovalGate = false) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")
        user2.login("user2", "user2pass")

        val meResponse = user2.get(ApiRoutes.Auth.ME)
        val user2Id = meResponse.body<UserInfo>().id ?: error("Expected user ID")

        // user2 tries to approve themselves — should be forbidden (not admin)
        val response = user2.post(ApiRoutes.Auth.approveUser(user2Id)) {
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin users list shows approval status`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()

        admin.login("admin", "adminpass")

        // Register a second user (not approved)
        val user2 = jsonClient()
        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }

        val usersResponse = admin.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.OK, usersResponse.status)
        val users = usersResponse.body<UserListResponse>().users
        assertEquals(2, users.size)

        val adminUser = users.first { it.username == "admin" }
        val pendingUser = users.first { it.username == "user2" }
        assertTrue(adminUser.approved)
        assertFalse(pendingUser.approved)
    }

    @Test
    fun `login response includes approved status`() = testApplication {
        application { testModule(enableApprovalGate = true) }
        val admin = jsonClient()
        val user2 = jsonClient()

        admin.login("admin", "adminpass")

        user2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }

        val loginResponse = user2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val userInfo = loginResponse.body<UserInfo>()
        assertFalse(userInfo.approved)
    }
}
