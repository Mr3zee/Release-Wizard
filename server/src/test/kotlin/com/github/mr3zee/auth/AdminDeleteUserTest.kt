package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.registerAndApproveUser
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminDeleteUserTest {

    // ── Helper functions ──────────────────────────────────────

    private suspend fun io.ktor.client.HttpClient.getUserId(): String {
        val meResponse = get(ApiRoutes.Auth.ME)
        val userInfo = meResponse.body<UserInfo>()
        return userInfo.id ?: error("Expected user ID")
    }

    private suspend fun io.ktor.client.HttpClient.deleteUser(
        userId: String,
        confirmTeamLeadTransfer: Boolean = false,
    ) = delete(ApiRoutes.Auth.deleteUser(userId)) {
        contentType(ContentType.Application.Json)
        setBody(AdminDeleteUserRequest(confirmTeamLeadTransfer = confirmTeamLeadTransfer))
    }

    private suspend fun io.ktor.client.HttpClient.getDeleteInfo(userId: String) =
        get(ApiRoutes.Auth.deleteUserPreCheck(userId))

    private suspend fun io.ktor.client.HttpClient.promoteToAdmin(
        superadminClient: io.ktor.client.HttpClient,
        userId: String,
        username: String,
        password: String,
    ) {
        superadminClient.put(ApiRoutes.Auth.userRole(userId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRoleRequest(role = UserRole.ADMIN))
        }
        // Re-login to refresh the session role
        post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = username, password = password))
        }
    }

    // ── Admin delete basic flows ──────────────────────────────

    @Test
    fun `admin can delete a regular user`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")
        val regularUserId = userClient.getUserId()

        val response = superadminClient.deleteUser(regularUserId)
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify user is gone
        val usersResponse = superadminClient.get(ApiRoutes.Auth.USERS)
        val users = usersResponse.body<UserListResponse>().users
        assertFalse(users.any { it.username == "regularuser" })
    }

    @Test
    fun `admin cannot delete themselves`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()

        val response = superadminClient.deleteUser(superadminId)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("CANNOT_DELETE_SELF", error.code)
    }

    @Test
    fun `admin cannot delete themselves via case-different UUID`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()
        val caseFlippedId = superadminId.uppercase()

        val response = superadminClient.deleteUser(caseFlippedId)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("CANNOT_DELETE_SELF", error.code)
    }

    @Test
    fun `non-admin cannot delete users`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")
        val superadminId = superadminClient.getUserId()

        // Regular user tries to delete superadmin
        val response = userClient.deleteUser(superadminId)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `delete with no body defaults to no team lead transfer`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")
        val regularUserId = userClient.getUserId()

        // DELETE with no body at all
        val response = superadminClient.delete(ApiRoutes.Auth.deleteUser(regularUserId))
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Superadmin guards ─────────────────────────────────────

    @Test
    fun `superadmin can delete an admin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val adminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        adminClient.registerAndApproveUser(superadminClient, "admin2", "adminpass2")
        val admin2Id = adminClient.getUserId()

        // Promote to ADMIN (re-login to refresh session)
        adminClient.promoteToAdmin(superadminClient, admin2Id, "admin2", "adminpass2")

        // Superadmin can delete the admin
        val response = superadminClient.deleteUser(admin2Id)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `regular admin cannot delete another admin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val admin1Client = jsonClient()
        val admin2Client = jsonClient()

        superadminClient.login("superadmin", "superpass")
        admin1Client.registerAndApproveUser(superadminClient, "admin1", "adminpass1")
        admin2Client.registerAndApproveUser(superadminClient, "admin2", "adminpass2")
        val admin1Id = admin1Client.getUserId()
        val admin2Id = admin2Client.getUserId()

        admin1Client.promoteToAdmin(superadminClient, admin1Id, "admin1", "adminpass1")
        admin2Client.promoteToAdmin(superadminClient, admin2Id, "admin2", "adminpass2")

        // admin1 tries to delete admin2
        val response = admin1Client.deleteUser(admin2Id)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("SUPERADMIN_REQUIRED", error.code)
    }

    @Test
    fun `nobody can delete the superadmin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val adminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()

        adminClient.registerAndApproveUser(superadminClient, "admin2", "adminpass2")
        val admin2Id = adminClient.getUserId()
        adminClient.promoteToAdmin(superadminClient, admin2Id, "admin2", "adminpass2")

        // Admin tries to delete superadmin
        val response = adminClient.deleteUser(superadminId)
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("CANNOT_DELETE_SUPERADMIN", error.code)
    }

    @Test
    fun `cannot promote anyone to superadmin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")
        val userId = userClient.getUserId()

        val response = superadminClient.put(ApiRoutes.Auth.userRole(userId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRoleRequest(role = UserRole.SUPERADMIN))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `cannot change superadmin role`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()

        val response = superadminClient.put(ApiRoutes.Auth.userRole(superadminId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRoleRequest(role = UserRole.USER))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── Team lead transfer ────────────────────────────────────

    @Test
    fun `delete sole team lead without confirmation returns TEAM_LEAD_TRANSFER_REQUIRED`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "teamlead", "leadpass")
        val leadId = userClient.getUserId()

        // User creates a team (becomes TEAM_LEAD)
        userClient.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "LeadTeam"))
        }

        // Delete without confirmation
        val response = superadminClient.deleteUser(leadId)
        assertEquals(HttpStatusCode.Conflict, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("TEAM_LEAD_TRANSFER_REQUIRED", error.code)
    }

    @Test
    fun `delete sole team lead with confirmation transfers lead and deletes`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "teamlead", "leadpass")
        val leadId = userClient.getUserId()

        // User creates a team
        val teamResponse = userClient.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "TransferTeam"))
        }
        val teamId = teamResponse.body<TeamResponse>().team.id

        // Delete with confirmation
        val response = superadminClient.deleteUser(leadId, confirmTeamLeadTransfer = true)
        assertEquals(HttpStatusCode.OK, response.status)

        // Verify the superadmin is now team lead
        val membersResponse = superadminClient.get(ApiRoutes.Teams.members(teamId.value))
        val members = membersResponse.body<TeamMemberListResponse>().members
        assertTrue(members.any { it.username == "superadmin" && it.role == TeamRole.TEAM_LEAD })

        // Verify deleted user is gone
        assertFalse(members.any { it.username == "teamlead" })
    }

    @Test
    fun `delete user who is not a team lead succeeds without transfer`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "nonlead", "nonleadpass")
        val userId = userClient.getUserId()

        // User is not in any team — delete should succeed without transfer
        val response = superadminClient.deleteUser(userId)
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── Pre-check endpoint ────────────────────────────────────

    @Test
    fun `pre-check returns canDelete true for regular user`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")
        val userId = userClient.getUserId()

        val response = superadminClient.getDeleteInfo(userId)
        assertEquals(HttpStatusCode.OK, response.status)
        val preCheck = response.body<DeleteUserPreCheckResponse>()
        assertTrue(preCheck.canDelete)
        assertFalse(preCheck.requiresSuperAdmin)
        assertTrue(preCheck.affectedTeams.isEmpty())
    }

    @Test
    fun `pre-check returns canDelete false for self`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()

        val response = superadminClient.getDeleteInfo(superadminId)
        assertEquals(HttpStatusCode.OK, response.status)
        val preCheck = response.body<DeleteUserPreCheckResponse>()
        assertFalse(preCheck.canDelete)
    }

    @Test
    fun `pre-check returns canDelete false for superadmin target`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val adminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()

        adminClient.registerAndApproveUser(superadminClient, "admin2", "adminpass2")
        val admin2Id = adminClient.getUserId()
        adminClient.promoteToAdmin(superadminClient, admin2Id, "admin2", "adminpass2")

        val response = adminClient.getDeleteInfo(superadminId)
        assertEquals(HttpStatusCode.OK, response.status)
        val preCheck = response.body<DeleteUserPreCheckResponse>()
        assertFalse(preCheck.canDelete)
    }

    @Test
    fun `pre-check returns requiresSuperAdmin when admin targets another admin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val admin1Client = jsonClient()
        val admin2Client = jsonClient()

        superadminClient.login("superadmin", "superpass")
        admin1Client.registerAndApproveUser(superadminClient, "admin1", "adminpass1")
        admin2Client.registerAndApproveUser(superadminClient, "admin2", "adminpass2")
        val admin1Id = admin1Client.getUserId()
        val admin2Id = admin2Client.getUserId()

        admin1Client.promoteToAdmin(superadminClient, admin1Id, "admin1", "adminpass1")
        admin2Client.promoteToAdmin(superadminClient, admin2Id, "admin2", "adminpass2")

        val response = admin1Client.getDeleteInfo(admin2Id)
        assertEquals(HttpStatusCode.OK, response.status)
        val preCheck = response.body<DeleteUserPreCheckResponse>()
        assertFalse(preCheck.canDelete)
        assertTrue(preCheck.requiresSuperAdmin)
    }

    @Test
    fun `pre-check returns affected teams for sole team lead`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        userClient.registerAndApproveUser(superadminClient, "teamlead", "leadpass")
        val leadId = userClient.getUserId()

        userClient.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "AffectedTeam"))
        }

        val response = superadminClient.getDeleteInfo(leadId)
        assertEquals(HttpStatusCode.OK, response.status)
        val preCheck = response.body<DeleteUserPreCheckResponse>()
        assertTrue(preCheck.canDelete)
        assertEquals(1, preCheck.affectedTeams.size)
        assertEquals("AffectedTeam", preCheck.affectedTeams.first().teamName)
    }

    @Test
    fun `pre-check requires admin`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()
        val userClient = jsonClient()

        superadminClient.login("superadmin", "superpass")
        val superadminId = superadminClient.getUserId()
        userClient.registerAndApproveUser(superadminClient, "regularuser", "userpass")

        val response = userClient.getDeleteInfo(superadminId)
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `delete nonexistent user returns not found`() = testApplication {
        application { testModule() }
        val superadminClient = jsonClient()

        superadminClient.login("superadmin", "superpass")

        val response = superadminClient.deleteUser("00000000-0000-0000-0000-000000000000")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
