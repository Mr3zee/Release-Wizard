package com.github.mr3zee.teams

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamSecurityTest {

    @Test
    fun `cannot change own role`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")
        val teamId = client.createTestTeam("Self Role Team")

        val me = client.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val myId = me.id ?: error("No user ID")

        val response = client.put(ApiRoutes.Teams.member(teamId.value, myId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("Cannot change your own role"))
    }

    @Test
    fun `cannot remove self via removeMember endpoint`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Self Remove Team")

        val user2 = jsonClient()
        user2.login("user2", "user2pass")
        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "user2"))
        }
        val invites = user2.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        user2.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))

        admin.put(ApiRoutes.Teams.member(teamId.value, invites.invites.first().invitedUserId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.TEAM_LEAD))
        }

        val me2 = user2.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val user2Id = me2.id ?: error("No user ID")

        val response = user2.delete(ApiRoutes.Teams.member(teamId.value, user2Id))
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("Use the leave endpoint"))
    }

    @Test
    fun `team name with control characters is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "Bad\u0000Name"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("invalid characters"))
    }

    @Test
    fun `team name with format characters is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "Name\u200BWit\u200BhZWS"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("invalid characters"))
    }

    @Test
    fun `team name is trimmed on create`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")

        val response = client.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "  Trimmed Team  "))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val team = response.body<TeamResponse>()
        assertEquals("Trimmed Team", team.team.name)
    }

    @Test
    fun `team name is trimmed on update`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")
        val teamId = client.createTestTeam("Original Name")

        val response = client.put(ApiRoutes.Teams.byId(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(name = "  Updated Name  "))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val team = response.body<TeamResponse>()
        assertEquals("Updated Name", team.team.name)
    }

    @Test
    fun `update team name rejects control characters`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login("admin", "adminpass")
        val teamId = client.createTestTeam("Clean Name")

        val response = client.put(ApiRoutes.Teams.byId(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(name = "Bad\u0001Update"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `expired invites are not returned for user`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Expiry Team")

        val user2 = jsonClient()
        user2.login("invitee", "inviteepass")

        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "invitee"))
        }

        val invites = user2.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        assertTrue(invites.invites.isNotEmpty())
        assertTrue(invites.invites.all { it.status == InviteStatus.PENDING })
    }

    @Test
    fun `expired invites are not returned for team`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Team Expiry")

        val user2 = jsonClient()
        user2.login("teamuser", "teamuserpass")

        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "teamuser"))
        }

        val teamInvites = admin.get(ApiRoutes.Teams.invites(teamId.value)).body<InviteListResponse>()
        assertTrue(teamInvites.invites.isNotEmpty())
        assertTrue(teamInvites.invites.all { it.status == InviteStatus.PENDING })
    }

    // --- Invite Error Messages ---

    @Test
    fun `invite non-existent user returns User not found`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Invite Error Team")

        val response = admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "nonexistentuser"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("User not found"), "Expected 'User not found' but got: ${error.error}")
    }

    @Test
    fun `invite yourself returns Cannot invite yourself`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Self Invite Team")

        val response = admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "admin"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("Cannot invite yourself"), "Expected 'Cannot invite yourself' but got: ${error.error}")
    }

    @Test
    fun `invite existing member returns already a member`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Member Invite Team")

        // Register and add a second user to the team
        val user2 = jsonClient()
        user2.login("member2", "member2pass")
        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "member2"))
        }
        val invites = user2.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        user2.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))

        // Now try to invite the already-member user again
        val response = admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "member2"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("already a member"), "Expected 'already a member' but got: ${error.error}")
    }

    @Test
    fun `invite user with pending invite returns Invite already sent`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Duplicate Invite Team")

        // Register a second user
        val user2 = jsonClient()
        user2.login("pendinguser", "pendinguserpass")

        // Send first invite
        val firstInvite = admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "pendinguser"))
        }
        assertEquals(HttpStatusCode.Created, firstInvite.status)

        // Send second invite to same user
        val response = admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "pendinguser"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertTrue(error.error.contains("Invite already sent"), "Expected 'Invite already sent' but got: ${error.error}")
    }
}
