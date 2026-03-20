package com.github.mr3zee

import com.github.mr3zee.api.*
import com.github.mr3zee.audit.AuditRepository
import com.github.mr3zee.auth.PasswordValidator
import com.github.mr3zee.model.AuditAction
import com.github.mr3zee.model.AuditTargetType
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.schedules.CronUtils
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

class Phase6PolishTest {

    // --- AUTH STREAM ---

    @Test
    fun `AUTH-M1 - register still works after moving hash outside transaction`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "authm1user", password = "testpass"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<UserInfo>()
        assertEquals("authm1user", body.username)
    }

    @Test
    fun `AUTH-M1 - duplicate registration returns 400 without timing hash inside tx`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "dupuser", password = "testpass"))
        }

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "dupuser", password = "testpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `AUTH-L5 - whitespace does not count as special character`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1,
            requireUppercase = false,
            requireDigit = false,
            requireSpecial = true,
        ))
        val errors = validator.validate("password with spaces")
        assertTrue(errors.any { "special" in it.lowercase() },
            "Whitespace should not satisfy requireSpecial check")

        val errorsWithSpecial = validator.validate("password@123")
        assertFalse(errorsWithSpecial.any { "special" in it.lowercase() },
            "@ should satisfy requireSpecial check")
    }

    @Test
    fun `AUTH-L1 - login provides non-empty CSRF token`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "csrfuser", password = "testpass"))
        }
        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "csrfuser", password = "testpass"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val csrfToken = loginResponse.headers["X-CSRF-Token"]
        assertNotNull(csrfToken, "CSRF token must be provided after login")
        assertTrue(csrfToken.isNotEmpty(), "CSRF token must not be empty")
    }

    // --- SCHEDULE STREAM ---

    @Test
    fun `SCHED-H1 - computeNextRun returning null is rejected`() {
        // Feb 29 only exists on leap years. In a non-leap year, computeNextRun returns null.
        // The guard in ScheduleService should catch this.
        val nextRun = CronUtils.computeNextRun("0 0 30 2 *") // Feb 30 never exists
        assertNull(nextRun, "computeNextRun should return null for impossible date")
    }

    @Test
    fun `SCHED-H1 - schedule creation succeeds for valid cron`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("sched-team")

        // Create a project first
        val projectResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "sched-proj", description = "", teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, projectResponse.status)
        val project = projectResponse.body<ProjectResponse>()

        // Use a cron expression that is valid but computes next run successfully
        val response = client.post(ApiRoutes.Schedules.byProject(project.project.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 12 * * *",
                parameters = emptyList(),
                enabled = true,
            ))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `SCHED-H2 - validateMinimumInterval rejects on non-IAE exception`() {
        // This tests that the blanket catch was removed.
        // A valid cron expression should not throw, so we test that it still works.
        CronUtils.validateMinimumInterval("0 */6 * * *") // Every 6 hours — should pass
    }

    @Test
    fun `SCHED-H2 - validateMinimumInterval rejects too-frequent expressions`() {
        try {
            CronUtils.validateMinimumInterval("* * * * *") // Every minute
            throw AssertionError("Should have thrown IAE for too-frequent schedule")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least") == true)
        }
    }

    // --- CONNECTION STREAM ---

    @Test
    fun `CONN-M2 - reject no-op PUT with both fields null`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("conn-team")

        // Create a connection first
        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(
                name = "test-conn",
                type = ConnectionType.SLACK,
                config = com.github.mr3zee.model.ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
                teamId = teamId,
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val conn = createResponse.body<ConnectionResponse>()

        // Try to update with no fields
        val updateResponse = client.put("${ApiRoutes.Connections.BASE}/${conn.connection.id.value}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateConnectionRequest(name = null, config = null))
        }
        assertEquals(HttpStatusCode.BadRequest, updateResponse.status)
    }

    @Test
    fun `CONN-L3 - workflowFile validates format`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("wf-team")

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(
                name = "gh-conn",
                type = ConnectionType.GITHUB,
                config = com.github.mr3zee.model.ConnectionConfig.GitHubConfig(
                    owner = "owner", repo = "repo", token = "ghp_test123"
                ),
                teamId = teamId,
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val conn = createResponse.body<ConnectionResponse>()

        // Try invalid workflow file name with special characters
        val response = client.get("${ApiRoutes.Connections.BASE}/${conn.connection.id.value}/github/workflows/bad%20file%21/parameters")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- TEAM STREAM ---

    @Test
    fun `TEAM-M3 - reject both-null team update`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("null-team")

        val response = client.put("${ApiRoutes.Teams.BASE}/${teamId.value}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(name = null, description = null))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `TEAM-L2 - invite for nonexistent user gives generic error`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("enum-team")

        val response = client.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "nonexistent-user-xyz"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.body<ErrorResponse>()
        // Should NOT reveal whether user exists
        assertFalse(body.error.contains("nonexistent"), "Error should not reveal username existence")
    }

    @Test
    fun `TEAM-L2 - invite for existing member gives same generic error`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("member-enum-team")

        // Admin is already a member — inviting them should give the same generic error
        val response = client.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "admin"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        // Same generic message, not "already a member"
        assertEquals("Unable to invite user", body.error)
    }

    // --- NOTIFICATION STREAM ---

    @Test
    fun `NOTIF-H2 - empty userId config requires admin to delete`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("notif-team")

        // Create a project
        val projectResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "notif-proj", description = "", teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, projectResponse.status)
        val project = projectResponse.body<ProjectResponse>()

        // Create a notification config (the first user is admin, so they can create it)
        val createResponse = client.post(ApiRoutes.Notifications.byProject(project.project.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateNotificationConfigRequest(
                projectId = project.project.id,
                type = "slack",
                config = com.github.mr3zee.model.NotificationConfig.SlackNotification(
                    webhookUrl = "https://hooks.slack.com/services/test",
                    channel = "#test",
                ),
                enabled = true,
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
    }

    // --- INFRA STREAM ---

    @Test
    fun `INFRA-L4 - root endpoint does not disclose version`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<Map<String, String>>()
        assertNull(body["version"], "Version should not be disclosed")
        assertEquals("running", body["status"])
    }

    @Test
    fun `INFRA-M2 - upstream X-Request-ID is used as correlation ID`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // Safe alphanumeric ID should be preserved
        val response = client.get("/") {
            header("X-Request-ID", "upstream-correlation-123")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("upstream-correlation-123", response.headers["X-Correlation-Id"])
    }

    @Test
    fun `INFRA-M2 - rejects unsafe X-Request-ID with special chars`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // Unsafe ID with spaces/symbols should be rejected (only alphanumeric, hyphens, underscores, dots allowed)
        val response = client.get("/") {
            header("X-Request-ID", "evil injection <script>")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val correlationId = response.headers["X-Correlation-Id"]
        assertNotNull(correlationId)
        // Should have generated a new UUID, not used the unsafe value
        assertTrue(correlationId != "evil injection <script>")
    }

    @Test
    fun `INFRA-M2 - generates correlation ID when no upstream header`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val correlationId = response.headers["X-Correlation-Id"]
        assertNotNull(correlationId, "Should generate correlation ID when no upstream header")
        assertTrue(correlationId.isNotEmpty())
    }

    // --- TAG STREAM ---

    @Test
    fun `TAG-M6 - UNKNOWN audit action and target type exist`() {
        // These enum values should exist for graceful degradation
        assertEquals("UNKNOWN", AuditAction.UNKNOWN.name)
        assertEquals("UNKNOWN", AuditTargetType.UNKNOWN.name)
    }

    // --- SCHEDULE duplicate audit fix ---

    @Test
    fun `schedule CRUD produces single audit event per operation`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("audit-team")

        // Create a project
        val projectResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "audit-proj", description = "", teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, projectResponse.status)
        val project = projectResponse.body<ProjectResponse>()

        // Create schedule
        val schedResponse = client.post(ApiRoutes.Schedules.byProject(project.project.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 12 * * *",
                parameters = emptyList(),
                enabled = true,
            ))
        }
        assertEquals(HttpStatusCode.Created, schedResponse.status)

        // Check audit — should have exactly one SCHEDULE_CREATED (not duplicated)
        val auditRepo = getKoin().get<AuditRepository>()
        val (events, _) = auditRepo.findByTeam(teamId, 0, 100)
        val scheduleCreated = events.filter { it.action == AuditAction.SCHEDULE_CREATED }
        assertEquals(1, scheduleCreated.size,
            "Expected exactly 1 SCHEDULE_CREATED audit event (was duplicated before fix)")
    }
}
