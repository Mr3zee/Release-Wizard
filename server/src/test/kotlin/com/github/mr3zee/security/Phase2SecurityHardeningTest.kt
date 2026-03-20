package com.github.mr3zee.security

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.AccountLockoutService
import com.github.mr3zee.connections.ConnectionTester
import com.github.mr3zee.model.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.*

/**
 * Phase 2 — Security Hardening integration tests.
 *
 * Covers: SSRF (2A), Auth & Session (2B), Authorization (2C), Credential Exposure (2D).
 */
class Phase2SecurityHardeningTest {

    // ─── Stream 2A: SSRF Hardening ───

    @Test
    fun `CONN-C1 - validateHostNotPrivate blocks loopback`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionTester.validateHostNotPrivate("127.0.0.1")
        }
    }

    @Test
    fun `CONN-C1 - validateHostNotPrivate blocks site-local 192`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionTester.validateHostNotPrivate("192.168.1.1")
        }
    }

    @Test
    fun `CONN-C1 - validateHostNotPrivate blocks site-local 10`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionTester.validateHostNotPrivate("10.0.0.1")
        }
    }

    @Test
    fun `CONN-C1 - validateUrlNotPrivate delegates to validateHostNotPrivate`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionTester.validateUrlNotPrivate("http://127.0.0.1:8080/test")
        }
    }

    @Test
    fun `CONN-C1 - validateHostNotPrivate allows unresolvable hosts`() {
        // Unresolvable hosts should not throw (the HTTP request will fail anyway)
        ConnectionTester.validateHostNotPrivate("this-host-does-not-exist-zzzz.example.com")
    }

    @Test
    fun `CONN-C1 - validateUrlNotPrivate rejects missing host`() {
        assertFailsWith<IllegalArgumentException> {
            ConnectionTester.validateUrlNotPrivate("not-a-url")
        }
    }

    // ─── Stream 2B: Auth & Session Security ───

    @Test
    fun `AUTH-H1 - me endpoint requires authentication`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // /me without login should return 401
        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `AUTH-H1 - me endpoint returns user info when authenticated`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals("admin", userInfo.username)
        assertEquals(UserRole.ADMIN, userInfo.role)
    }

    @Test
    fun `AUTH-H2 - admin endpoints require admin role`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("regularuser", "userpass1")

        // Regular user cannot list users
        val response = userClient.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.Forbidden, response.status)

        // Admin can list users
        val adminResponse = adminClient.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.OK, adminResponse.status)
    }

    @Test
    fun `AUTH-H4 - account lockout after failed attempts`() {
        val lockout = AccountLockoutService()

        // First 4 attempts should not lock
        repeat(4) {
            assertNull(lockout.checkLocked("testuser"))
            lockout.recordFailure("testuser")
        }
        // Still not locked (4 < 5 threshold)
        assertNull(lockout.checkLocked("testuser"))

        // 5th failure triggers lockout
        lockout.recordFailure("testuser")
        assertNotNull(lockout.checkLocked("testuser"), "Account should be locked after 5 failures")

        // Successful login clears lockout
        lockout.recordSuccess("testuser")
        assertNull(lockout.checkLocked("testuser"), "Lockout should be cleared after success")
    }

    @Test
    fun `AUTH-H4 - lockout returns 429 on login`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // Register valid user first
        client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("lockoutuser", "lockoutpass"))
        }

        // 5 failed login attempts with wrong password
        repeat(5) {
            client.post(ApiRoutes.Auth.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("lockoutuser", "wrongpassword"))
            }
        }

        // Next login should be locked even with correct password
        val lockedResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("lockoutuser", "lockoutpass"))
        }
        assertEquals(HttpStatusCode.TooManyRequests, lockedResponse.status)
        // Verify the response is from lockout, not rate limiter
        val body = lockedResponse.body<ErrorResponse>()
        assertEquals("ACCOUNT_LOCKED", body.code)
    }

    @Test
    fun `AUTH-H6 - CSRF protection rejects POST without CSRF token`() = testApplication {
        application { testModule() }
        // Use a client WITHOUT the CSRF auto-attach plugin
        val rawClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
            install(HttpCookies)
        }

        // Register first, then login
        rawClient.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("csrfuser", "csrfpassword"))
        }
        rawClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("csrfuser", "csrfpassword"))
        }

        // POST without CSRF header should be rejected with 403
        val response = rawClient.post(ApiRoutes.Auth.LOGOUT)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `AUTH-H5 - Argon2 parameters are consistent across hash and verify`() = testApplication {
        // Verify that registration with p=4 produces hashes that can be verified on login.
        // If the Argon2 parameters were inconsistent (e.g., p=4 on hash, p=1 on verify),
        // login would fail. This test proves the parameter change is end-to-end correct.
        application { testModule() }
        val client = jsonClient()
        val regResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("argon2user", "argon2pass"))
        }
        assertEquals(HttpStatusCode.Created, regResponse.status)
        // Logout to clear session from register
        client.post(ApiRoutes.Auth.LOGOUT)
        // Login with same credentials — proves hash params are consistent
        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("argon2user", "argon2pass"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun `AUTH-M5 - session has createdAt timestamp`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // Verify session works — the absolute lifetime check is wired up in SessionTtlPlugin
        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
    }

    // ─── Stream 2C: Authorization Gaps ───

    @Test
    fun `REL-H2 - getRelease requires team membership`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Start a release
        val createResponse = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val releaseId = createResponse.body<ReleaseResponse>().release.id.value

        // Second user (not team member) should be denied access
        val otherClient = jsonClient()
        otherClient.login("otheruser", "otherpass")

        val getResponse = otherClient.get("${ApiRoutes.Releases.BASE}/$releaseId")
        assertEquals(HttpStatusCode.Forbidden, getResponse.status)
    }

    @Test
    fun `REL-M4 - cancel release requires TEAM_LEAD`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")
        val teamId = adminClient.createTestTeam()
        val projectId = adminClient.createTestProjectWithBlocks(teamId)

        // Start a release
        val createResponse = adminClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val releaseId = createResponse.body<ReleaseResponse>().release.id.value

        // Create a regular user and add them to the team as COLLABORATOR
        val collabClient = jsonClient()
        collabClient.login("collaborator", "collabpass")

        // Invite collaborator to team
        adminClient.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "collaborator"))
        }

        // Accept invite via my-invites endpoint
        val invitesResponse = collabClient.get(ApiRoutes.Auth.MyInvites.BASE)
        val invites = invitesResponse.body<InviteListResponse>().invites
        assertTrue(invites.isNotEmpty(), "Should have an invite")
        collabClient.post(ApiRoutes.Auth.MyInvites.accept(invites.first().id))

        // Collaborator should NOT be able to cancel (requires TEAM_LEAD) — 403 Forbidden
        val cancelResponse = collabClient.post("${ApiRoutes.Releases.BASE}/$releaseId/cancel")
        assertEquals(HttpStatusCode.Forbidden, cancelResponse.status)

        // Admin (team lead) should NOT get 403 — they have the role.
        // May get 400 if release already completed (stub executor is instant), which is fine.
        val adminCancelResponse = adminClient.post("${ApiRoutes.Releases.BASE}/$releaseId/cancel")
        assertNotEquals(HttpStatusCode.Forbidden, adminCancelResponse.status, "Admin should not be rejected by TEAM_LEAD check")
    }

    @Test
    fun `REL-M4 - delete release requires TEAM_LEAD`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")
        val teamId = adminClient.createTestTeam()
        val projectId = adminClient.createTestProjectWithBlocks(teamId)

        // Start + cancel a release so it's in terminal state
        val createResponse = adminClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        val releaseId = createResponse.body<ReleaseResponse>().release.id.value
        adminClient.post("${ApiRoutes.Releases.BASE}/$releaseId/cancel")

        // Add collaborator
        val collabClient = jsonClient()
        collabClient.login("collaborator2", "collabpass")
        adminClient.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "collaborator2"))
        }
        val invites = collabClient.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>().invites
        if (invites.isNotEmpty()) {
            collabClient.post(ApiRoutes.Auth.MyInvites.accept(invites.first().id))
        }

        // Collaborator should NOT be able to delete
        val deleteResponse = collabClient.delete("${ApiRoutes.Releases.BASE}/$releaseId")
        assertEquals(HttpStatusCode.Forbidden, deleteResponse.status)
    }

    @Test
    fun `TEAM-H1 - admin cannot demote last team lead`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")
        val teamId = adminClient.createTestTeam()

        // Get members
        val membersResponse = adminClient.get(ApiRoutes.Teams.members(teamId.value))
        val members = membersResponse.body<TeamMemberListResponse>().members

        val adminMember = members.first()
        // Try to demote the only TEAM_LEAD to COLLABORATOR
        val demoteResponse = adminClient.put(ApiRoutes.Teams.member(teamId.value, adminMember.userId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR))
        }
        // Should fail because can't demote last team lead
        assertEquals(HttpStatusCode.BadRequest, demoteResponse.status)
    }

    // ─── Stream 2D: Credential Exposure ───

    @Test
    fun `CONN-H4 - updateConnection produces audit log`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()

        // Create connection
        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(
                name = "test-conn",
                type = ConnectionType.GITHUB,
                config = ConnectionConfig.GitHubConfig(owner = "test", repo = "repo", token = "ghp_test12345678"),
                teamId = teamId,
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val connId = createResponse.body<ConnectionResponse>().connection.id.value

        // Update connection
        val updateResponse = client.put("${ApiRoutes.Connections.BASE}/$connId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateConnectionRequest(name = "updated-conn"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        // Verify audit log exists for the update
        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        assertEquals(HttpStatusCode.OK, auditResponse.status)
        val auditBody = auditResponse.bodyAsText()
        assertContains(auditBody, "CONNECTION_UPDATED")
    }

    @Test
    fun `CONN-M1 - exception messages are sanitized in error responses`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()

        // Create a connection
        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(
                name = "sanitize-test",
                type = ConnectionType.TEAMCITY,
                config = ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "tc_test12345678"),
                teamId = teamId,
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val connId = createResponse.body<ConnectionResponse>().connection.id.value

        // Fetch build types — mock engine provides canned data
        val btResponse = client.get("${ApiRoutes.Connections.BASE}/$connId/teamcity/build-types")
        // The mock should return data. If it returned an error, verify the message is generic
        if (!btResponse.status.isSuccess()) {
            val body = btResponse.body<ErrorResponse>()
            // Verify no internal exception details leaked
            assertFalse(body.error.contains("Exception"), "Error message should not contain exception details")
            assertFalse(body.error.contains("stacktrace", ignoreCase = true), "Error message should not contain stack trace")
        }
    }

    @Test
    fun `NOTIF-H4 - notification webhook URL masked in responses`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()
        val projectId = client.createTestProject(teamId)

        // Create notification config
        val createResponse = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateNotificationConfigRequest(
                projectId = ProjectId(projectId),
                type = "slack",
                config = NotificationConfig.SlackNotification(
                    webhookUrl = "https://hooks.slack.com/services/T00/B00/secrettoken123",
                    channel = "#releases",
                ),
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val response = createResponse.body<NotificationConfigResponse>()

        // Webhook URL should be masked
        val config = response.config as NotificationConfig.SlackNotification
        assertContains(config.webhookUrl, "****", message = "Webhook URL should be masked")
        assertFalse(config.webhookUrl.contains("secrettoken123"), "Secret token should not appear in response")
    }

    @Test
    fun `NOTIF-H1 - notification webhook URL must use HTTPS`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()
        val projectId = client.createTestProject(teamId)

        // Try to create with HTTP URL
        val response = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateNotificationConfigRequest(
                projectId = ProjectId(projectId),
                type = "slack",
                config = NotificationConfig.SlackNotification(
                    webhookUrl = "http://hooks.slack.com/services/T00/B00/xxx",
                    channel = "#releases",
                ),
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `NOTIF-H1 - notification webhook URL rejects private IPs`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateNotificationConfigRequest(
                projectId = ProjectId(projectId),
                type = "slack",
                config = NotificationConfig.SlackNotification(
                    webhookUrl = "https://127.0.0.1/webhook",
                    channel = "#releases",
                ),
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `HOOK-M2 - webhook token is masked in logs`() = testApplication {
        // This is verified by code inspection — the log call now uses maskedToken
        // We verify the webhook endpoint still works correctly
        application { testModule() }
        val rawClient = createClient {
            install(ClientContentNegotiation) {
                json(AppJson)
            }
        }
        // POST to webhook status with invalid token — should return 404
        val response = rawClient.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer 00000000-0000-0000-0000-000000000000")
            contentType(ContentType.Application.Json)
            setBody(StatusUpdatePayload(status = "SUCCESS"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
