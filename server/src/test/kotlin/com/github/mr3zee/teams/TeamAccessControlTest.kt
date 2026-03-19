package com.github.mr3zee.teams

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.model.*
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamAccessControlTest {

    @Test
    fun `non-member cannot access team projects`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Admin Team")

        // Create a project under the team
        admin.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Team Project", teamId = teamId))
        }

        // Register a second user (non-admin, non-member)
        val user2 = jsonClient()
        user2.login("user2", "user2pass")

        // user2 should get empty projects (not a member of admin's team)
        val response = user2.get(ApiRoutes.Projects.BASE)
        val projects = response.body<ProjectListResponse>()
        assertEquals(0, projects.projects.size)
    }

    @Test
    fun `team member can see team projects`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Shared Team")

        admin.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Shared Project", teamId = teamId))
        }

        // Register user2 and invite them
        val user2 = jsonClient()
        user2.login("member", "memberpass")
        // Admin invites user2
        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "member"))
        }

        // user2 accepts invite
        val invites = user2.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        assertTrue(invites.invites.isNotEmpty())
        user2.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))

        // Now user2 should see the project
        val projects = user2.get(ApiRoutes.Projects.BASE + "?teamId=${teamId.value}").body<ProjectListResponse>()
        assertEquals(1, projects.projects.size)
        assertEquals("Shared Project", projects.projects.first().name)
    }

    @Test
    fun `collaborator can create but not delete projects`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Role Test Team")

        // Create user2 as collaborator
        val user2 = jsonClient()
        user2.login("collab", "collabpass")
        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "collab"))
        }
        val invites = user2.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        user2.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))

        // Collaborator can create a project
        val createResponse = user2.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Collab Project", teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val projectId = createResponse.body<ProjectResponse>().project.id

        // Collaborator cannot delete (requires TEAM_LEAD)
        val deleteResponse = user2.delete(ApiRoutes.Projects.byId(projectId.value))
        assertEquals(HttpStatusCode.Forbidden, deleteResponse.status)
    }

    @Test
    fun `team lead can delete projects`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Lead Team")

        val createResponse = admin.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Delete Me", teamId = teamId))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        // Admin (who is also TEAM_LEAD since they created the team) can delete
        val deleteResponse = admin.delete(ApiRoutes.Projects.byId(projectId.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `cannot demote the last team lead`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Solo Lead Team")

        val meResponse = admin.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val adminId = meResponse.id ?: error("No user ID")

        // Try to demote self — should fail
        val response = admin.put(ApiRoutes.Teams.member(teamId.value, adminId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `team CRUD operations work`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")

        // Create
        val createResponse = admin.post(ApiRoutes.Teams.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateTeamRequest(name = "CRUD Team", description = "Test description"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val team = createResponse.body<TeamResponse>()
        assertEquals("CRUD Team", team.team.name)
        assertEquals(1, team.memberCount)

        // List
        val listResponse = admin.get(ApiRoutes.Teams.BASE).body<TeamListResponse>()
        assertTrue(listResponse.teams.any { it.team.name == "CRUD Team" })

        // Update
        val updateResponse = admin.put(ApiRoutes.Teams.byId(team.team.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateTeamRequest(description = "Updated"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<TeamResponse>()
        assertEquals("Updated", updated.team.description)

        // Detail
        val detailResponse = admin.get(ApiRoutes.Teams.byId(team.team.id.value)).body<TeamDetailResponse>()
        assertEquals("CRUD Team", detailResponse.team.name)
        assertEquals(1, detailResponse.members.size)

        // Delete
        val deleteResponse = admin.delete(ApiRoutes.Teams.byId(team.team.id.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `non-member cannot start release for team project`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Release Team")

        // Create a project with blocks under admin's team
        val createResponse = admin.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProjectRequest(
                    name = "Team Release Project",
                    teamId = teamId,
                    dagGraph = DagGraph(
                        blocks = listOf(
                            Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                        ),
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val projectId = createResponse.body<ProjectResponse>().project.id

        // Register a non-member user
        val user2 = jsonClient()
        user2.login("outsider", "outsiderpass")

        // Attempt to start a release — should get 403 (Forbidden)
        val releaseResponse = user2.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectId))
        }
        assertEquals(HttpStatusCode.Forbidden, releaseResponse.status)
    }

    @Test
    fun `join request flow works`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Join Team")

        // Register user2
        val user2 = jsonClient()
        user2.login("joiner", "joinerpass")

        // user2 submits join request
        val joinResponse = user2.post(ApiRoutes.Teams.joinRequests(teamId.value))
        assertEquals(HttpStatusCode.Created, joinResponse.status)

        // Admin sees the request
        val requests = admin.get(ApiRoutes.Teams.joinRequests(teamId.value)).body<JoinRequestListResponse>()
        assertEquals(1, requests.requests.size)
        assertEquals("joiner", requests.requests.first().username)

        // Admin approves
        admin.post(ApiRoutes.Teams.approveJoinRequest(teamId.value, requests.requests.first().id))

        // user2 is now a member
        val members = admin.get(ApiRoutes.Teams.members(teamId.value)).body<TeamMemberListResponse>()
        assertEquals(2, members.members.size)
        assertTrue(members.members.any { it.username == "joiner" })
    }
}
