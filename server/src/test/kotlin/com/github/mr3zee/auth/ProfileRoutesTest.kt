package com.github.mr3zee.auth

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import com.github.mr3zee.model.TeamInvite
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.persistence.PasswordResetTokenTable
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class ProfileRoutesTest {

    // ── Change Username ─────────────────────────────────────────────────

    @Test
    fun `change username success`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "newadmin", currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals("newadmin", userInfo.username)

        // Verify /me returns the new username
        val meResponse = client.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val meInfo = meResponse.body<UserInfo>()
        assertEquals("newadmin", meInfo.username)
    }

    @Test
    fun `change username wrong password returns INVALID_PASSWORD`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "newadmin", currentPassword = "wrongpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INVALID_PASSWORD", error.code)
    }

    @Test
    fun `change username to duplicate returns USERNAME_TAKEN`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val client2 = jsonClient()
        client1.login("admin", "adminpass")
        client2.login("user2", "user2pass")

        val response = client1.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "user2", currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("USERNAME_TAKEN", error.code)
    }

    @Test
    fun `change username to blank returns VALIDATION_ERROR`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "", currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    @Test
    fun `change username too long returns VALIDATION_ERROR`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val longUsername = "a".repeat(65)
        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = longUsername, currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    @Test
    fun `change username to same as current succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "admin", currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfo = response.body<UserInfo>()
        assertEquals("admin", userInfo.username)
    }

    // ── Change Password ─────────────────────────────────────────────────

    @Test
    fun `change password success and login with new password`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "adminpass", newPassword = "newpassword1234"))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify can login with new password using a fresh client
        val freshClient = jsonClient()
        val loginResp = freshClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "newpassword1234"))
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)
    }

    @Test
    fun `old password stops working after change`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "adminpass", newPassword = "newpassword1234"))
        }

        // Try login with old password
        val freshClient = jsonClient()
        val loginResp = freshClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "adminpass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, loginResp.status)
    }

    @Test
    fun `change password wrong current password returns INVALID_PASSWORD`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "wrongpass", newPassword = "newpassword1234"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INVALID_PASSWORD", error.code)
    }

    @Test
    fun `change password invalid new password returns VALIDATION_ERROR`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "adminpass", newPassword = "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    // ── Delete Account ──────────────────────────────────────────────────

    @Test
    fun `delete account success clears session`() = testApplication {
        application { testModule() }
        // Create admin first, then a second user who will be deleted
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("deleteme", "deletemepass")

        val deleteResponse = userClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "deleteme", currentPassword = "deletemepass"))
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Subsequent /me should return 401
        val meResponse = userClient.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, meResponse.status)
    }

    @Test
    fun `delete account wrong password returns INVALID_PASSWORD`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("user2", "user2pass")

        val response = userClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "user2", currentPassword = "wrongpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INVALID_PASSWORD", error.code)
    }

    @Test
    fun `delete account wrong username confirmation returns USERNAME_MISMATCH`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("user2", "user2pass")

        val response = userClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "wrongname", currentPassword = "user2pass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("USERNAME_MISMATCH", error.code)
    }

    @Test
    fun `delete last admin blocked`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val response = adminClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "admin", currentPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("LAST_ADMIN", error.code)
    }

    @Test
    fun `delete last team lead blocked`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("teamlead", "teamleadpass")

        // User creates a team (becomes TEAM_LEAD)
        val teamResp = userClient.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "My Team"))
        }
        assertEquals(HttpStatusCode.Created, teamResp.status)

        // User is the only lead — deletion should be blocked
        val response = userClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "teamlead", currentPassword = "teamleadpass"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("LAST_TEAM_LEAD", error.code)
    }

    @Test
    fun `delete succeeds when another team lead exists`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val leadClient = jsonClient()
        leadClient.login("lead1", "lead1password")

        // lead1 creates a team (becomes TEAM_LEAD)
        val teamResp = leadClient.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "My Team"))
        }
        val teamId = teamResp.body<TeamResponse>().team.id

        // Invite admin to the team
        val inviteResp = leadClient.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "admin"))
        }
        val invite = inviteResp.body<TeamInvite>()
        adminClient.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")

        // Promote admin to TEAM_LEAD in that team
        val membersResp = leadClient.get(ApiRoutes.Teams.members(teamId.value))
        val members = membersResp.body<TeamMemberListResponse>()
        val adminMember = members.members.first { it.userId.value != leadClient.getUserId() }
        leadClient.put(ApiRoutes.Teams.member(teamId.value, adminMember.userId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.TEAM_LEAD))
        }

        // Now lead1 can delete their account (admin is also a lead)
        val deleteResp = leadClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "lead1", currentPassword = "lead1password"))
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)
    }

    @Test
    fun `delete account cascades team memberships`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("user2", "user2pass")

        // Admin creates a team
        val teamId = adminClient.createTestTeam("Cascade Team")

        // Invite user2 and accept
        val inviteResp = adminClient.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "user2"))
        }
        val invite = inviteResp.body<TeamInvite>()
        userClient.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")

        // Verify user2 is a member
        val membersBeforeResp = adminClient.get(ApiRoutes.Teams.members(teamId.value))
        val membersBefore = membersBeforeResp.body<TeamMemberListResponse>()
        assertTrue(membersBefore.members.any { it.username == "user2" }, "user2 should be a team member")

        // user2 deletes their account
        val deleteResp = userClient.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "user2", currentPassword = "user2pass"))
        }
        assertEquals(HttpStatusCode.OK, deleteResp.status)

        // Verify user2 is no longer a member (cascade)
        val membersAfterResp = adminClient.get(ApiRoutes.Teams.members(teamId.value))
        val membersAfter = membersAfterResp.body<TeamMemberListResponse>()
        assertTrue(membersAfter.members.none { it.username == "user2" }, "user2 membership should be gone after deletion")
    }

    // ── Password Reset ──────────────────────────────────────────────────

    @Test
    fun `password reset full flow`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("resetuser", "resetuserpass")

        // Get the user's ID
        val meResp = userClient.get(ApiRoutes.Auth.ME)
        val userId = meResp.body<UserInfo>().id
            ?: error("Expected non-null user ID from /me endpoint")

        // Admin generates a password reset token
        val generateResp = adminClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        assertEquals(HttpStatusCode.OK, generateResp.status)
        val resetLink = generateResp.body<PasswordResetLinkResponse>()
        assertTrue(resetLink.token.isNotEmpty(), "Token should not be empty")

        // Validate the token (unauthenticated endpoint)
        val validateResp = adminClient.post(ApiRoutes.Auth.VALIDATE_RESET_TOKEN) {
            contentType(ContentType.Application.Json)
            setBody(ValidateResetTokenRequest(token = resetLink.token))
        }
        assertEquals(HttpStatusCode.OK, validateResp.status)

        // Consume the token with a new password
        val consumeResp = adminClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "brandnewpass123"))
        }
        assertEquals(HttpStatusCode.OK, consumeResp.status)

        // Login with the new password
        val freshClient = jsonClient()
        val loginResp = freshClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "resetuser", password = "brandnewpass123"))
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)
    }

    @Test
    fun `non-admin cannot generate password reset token`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("regularuser", "regularuserpass")

        // Get admin's user ID (any user ID will do for the request)
        val meResp = adminClient.get(ApiRoutes.Auth.ME)
        val adminUserId = meResp.body<UserInfo>().id
            ?: error("Expected non-null admin user ID from /me endpoint")

        // Regular user tries to generate
        val response = userClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = adminUserId))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("FORBIDDEN", error.code)
    }

    @Test
    fun `validate invalid token returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.VALIDATE_RESET_TOKEN) {
            contentType(ContentType.Application.Json)
            setBody(ValidateResetTokenRequest(token = "nonexistent-random-token-value"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INVALID_TOKEN", error.code)
    }

    @Test
    fun `expired token cannot be consumed`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("expuser", "expuserpass")

        val meResp = userClient.get(ApiRoutes.Auth.ME)
        val userId = meResp.body<UserInfo>().id
            ?: error("Expected non-null user ID from /me endpoint")

        // Admin generates a token
        val generateResp = adminClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        assertEquals(HttpStatusCode.OK, generateResp.status)
        val resetLink = generateResp.body<PasswordResetLinkResponse>()

        // Backdate the token expiry in the DB
        val db = getKoin().get<Database>()
        suspendTransaction(db) {
            val pastInstant = Clock.System.now() - 1.hours
            PasswordResetTokenTable.update(
                where = { PasswordResetTokenTable.usedAt.isNull() }
            ) {
                it[expiresAt] = pastInstant
            }
        }

        // Try to consume the expired token
        val consumeResp = adminClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "brandnewpass123"))
        }
        assertEquals(HttpStatusCode.BadRequest, consumeResp.status)
        val error = consumeResp.body<ErrorResponse>()
        assertEquals("INVALID_TOKEN", error.code)
    }

    @Test
    fun `double consumption of token fails on second attempt`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("dbluser", "dbluserpass")

        val meResp = userClient.get(ApiRoutes.Auth.ME)
        val userId = meResp.body<UserInfo>().id
            ?: error("Expected non-null user ID from /me endpoint")

        // Admin generates a token
        val generateResp = adminClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        val resetLink = generateResp.body<PasswordResetLinkResponse>()

        // First consumption succeeds
        val firstConsumeResp = adminClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "firstnewpass123"))
        }
        assertEquals(HttpStatusCode.OK, firstConsumeResp.status)

        // Second consumption fails
        val secondConsumeResp = adminClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "secondnewpass123"))
        }
        assertEquals(HttpStatusCode.BadRequest, secondConsumeResp.status)
        val error = secondConsumeResp.body<ErrorResponse>()
        assertEquals("INVALID_TOKEN", error.code)
    }

    @Test
    fun `password reset with invalid new password returns VALIDATION_ERROR`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        adminClient.login("admin", "adminpass")

        val userClient = jsonClient()
        userClient.login("weakpw", "weakpwuserpass")

        val meResp = userClient.get(ApiRoutes.Auth.ME)
        val userId = meResp.body<UserInfo>().id
            ?: error("Expected non-null user ID from /me endpoint")

        // Admin generates a token
        val generateResp = adminClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        val resetLink = generateResp.body<PasswordResetLinkResponse>()

        // Try to consume with a weak password
        val consumeResp = adminClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, consumeResp.status)
        val error = consumeResp.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", error.code)
    }

    // ── Auth Checks (Unauthenticated) ───────────────────────────────────

    @Test
    fun `unauthenticated change username returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "hacker", currentPassword = "anypass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `unauthenticated change password returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "anypass", newPassword = "newpassword1234"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `unauthenticated delete account returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "anyone", currentPassword = "anypass"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── Password Policy Endpoint ────────────────────────────────────────

    @Test
    fun `password policy returns expected shape from test config`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Auth.PASSWORD_POLICY)
        assertEquals(HttpStatusCode.OK, response.status)
        val policy = response.body<PasswordPolicyResponse>()
        // testPasswordPolicyConfig() uses minLength=8, all requirements false
        assertEquals(8, policy.minLength)
        assertEquals(128, policy.maxLength)
        assertEquals(false, policy.requireUppercase)
        assertEquals(false, policy.requireDigit)
        assertEquals(false, policy.requireSpecial)

        // Verify Cache-Control header
        val cacheControl = response.headers[HttpHeaders.CacheControl]
        assertNotNull(cacheControl, "Expected Cache-Control header")
        assertTrue(cacheControl.contains("max-age=3600"), "Expected max-age=3600 in Cache-Control header")
    }

    @Test
    fun `default password policy has minLength 16`() = testApplication {
        application { testModuleWithPasswordPolicy(PasswordPolicyConfig()) }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Auth.PASSWORD_POLICY)
        assertEquals(HttpStatusCode.OK, response.status)
        val policy = response.body<PasswordPolicyResponse>()
        assertEquals(16, policy.minLength)
        assertEquals(128, policy.maxLength)
        assertEquals(true, policy.requireUppercase)
        assertEquals(true, policy.requireDigit)
        assertEquals(true, policy.requireSpecial)
    }

    // ── Session Invalidation After Password Change ─────────────────────

    @Test
    fun `session invalidated after password change`() = testApplication {
        // Use refresh threshold of 0 so every request triggers session refresh
        application { testModule(authConfig = testAuthConfig().copy(sessionRefreshThresholdSeconds = 0)) }
        val client1 = jsonClient()
        val client2 = jsonClient()

        // Both clients login as the same user
        client1.login("admin", "adminpass")
        client2.login("admin", "adminpass")

        // Verify client2 has a valid session
        val meResp1 = client2.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResp1.status)

        // Client1 changes the password
        val changeResp = client1.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "adminpass", newPassword = "newadminpass"))
        }
        assertEquals(HttpStatusCode.OK, changeResp.status)

        // Client2's session should be invalidated (predates password change)
        val meResp2 = client2.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, meResp2.status)
    }

    @Test
    fun `session invalidated after password reset`() = testApplication {
        // Use refresh threshold of 0 so every request triggers session refresh
        application { testModule(authConfig = testAuthConfig().copy(sessionRefreshThresholdSeconds = 0)) }
        val adminClient = jsonClient()
        val userClient = jsonClient()

        // Admin and user login
        adminClient.login("admin", "adminpass")
        userClient.login("user1", "userpass")

        // Verify user has a valid session
        val meResp1 = userClient.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.OK, meResp1.status)

        // Admin generates a reset token for the user
        val userId = userClient.getUserId()
        val resetResp = adminClient.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        assertEquals(HttpStatusCode.OK, resetResp.status)
        val resetLink = resetResp.body<PasswordResetLinkResponse>()

        // Use the reset token to change the user's password (unauthenticated)
        val unauthClient = jsonClient()
        val consumeResp = unauthClient.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "resetpassword"))
        }
        assertEquals(HttpStatusCode.OK, consumeResp.status)

        // User's old session should be invalidated
        val meResp2 = userClient.get(ApiRoutes.Auth.ME)
        assertEquals(HttpStatusCode.Unauthorized, meResp2.status)
    }

    // ── Same Password Rejected ──────────────────────────────────────────

    @Test
    fun `change password to same value returns SAME_PASSWORD`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "adminpass", newPassword = "adminpass"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("SAME_PASSWORD", error.code)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Extract the current user's ID from /me.
     */
    private suspend fun HttpClient.getUserId(): String {
        val meResp = get(ApiRoutes.Auth.ME)
        return meResp.body<UserInfo>().id
            ?: error("Expected non-null user ID from /me endpoint")
    }
}
