package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.createTestProject
import com.github.mr3zee.model.UserId
import com.github.mr3zee.testModule
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectLockRoutesTest {

    /**
     * Helper to invite a user to a team and accept the invite.
     * Requires admin to be logged in on [adminClient] and the target user logged in on [userClient].
     */
    private suspend fun inviteAndAccept(
        adminClient: HttpClient,
        userClient: HttpClient,
        teamId: com.github.mr3zee.model.TeamId,
    ) {
        val meResponse = userClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val userId = meResponse.id ?: error("No user ID")
        adminClient.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(userId = UserId(userId)))
        }
        val invites = userClient.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        userClient.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))
    }

    @Test
    fun `acquire lock returns 200 with lock info`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, response.status)
        val lock = response.body<ProjectLockInfo>()
        assertEquals("admin", lock.username)
    }

    @Test
    fun `double acquire by same user returns 200 (idempotent)`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val r1 = client.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, r1.status)

        val r2 = client.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, r2.status)
    }

    @Test
    fun `acquire by different user returns 409 with lock holder info`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("user1", "password1")
        val projectId = client1.createTestProject(teamId)

        // User1 acquires lock
        val r1 = client1.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, r1.status)

        // User2 joins team
        val client2 = jsonClient()
        client2.login("user2", "password2")
        inviteAndAccept(client1, client2, teamId)

        // User2 tries to acquire lock
        val r2 = client2.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.Conflict, r2.status)
        val conflict = r2.body<ProjectLockConflictResponse>()
        assertEquals("LOCK_CONFLICT", conflict.code)
        assertEquals("user1", conflict.lock.username)
    }

    @Test
    fun `release returns 204 and subsequent acquire succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.Projects.lock(projectId))
        val releaseResponse = client.delete(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.NoContent, releaseResponse.status)

        // Can re-acquire
        val r2 = client.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, r2.status)
    }

    @Test
    fun `heartbeat extends TTL and returns updated lock info`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val acquireResponse = client.post(ApiRoutes.Projects.lock(projectId))
        val originalLock = acquireResponse.body<ProjectLockInfo>()

        val heartbeatResponse = client.put(ApiRoutes.Projects.lockHeartbeat(projectId))
        assertEquals(HttpStatusCode.OK, heartbeatResponse.status)
        val updatedLock = heartbeatResponse.body<ProjectLockInfo>()
        assertTrue(updatedLock.expiresAt >= originalLock.expiresAt)
    }

    @Test
    fun `heartbeat by non-holder returns 409`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("user1", "password1")
        val projectId = client1.createTestProject(teamId)

        // User1 acquires lock
        client1.post(ApiRoutes.Projects.lock(projectId))

        // User2 joins team
        val client2 = jsonClient()
        client2.login("user2", "password2")
        inviteAndAccept(client1, client2, teamId)

        val heartbeatResponse = client2.put(ApiRoutes.Projects.lockHeartbeat(projectId))
        assertEquals(HttpStatusCode.Conflict, heartbeatResponse.status)
    }

    @Test
    fun `get lock returns 200 when lock exists`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.Projects.lock(projectId))

        val response = client.get(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, response.status)
        val lock = response.body<ProjectLockInfo>()
        assertEquals("admin", lock.username)
    }

    @Test
    fun `get lock returns 404 when no lock exists`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.get(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `force release by admin of own lock succeeds`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("admin", "adminpass")
        val projectId = client1.createTestProject(teamId)

        // Admin acquires lock
        client1.post(ApiRoutes.Projects.lock(projectId))

        // Admin force-releases
        val response = client1.delete(ApiRoutes.Projects.lock(projectId)) {
            parameter("force", true)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        // Lock should be gone
        val getResponse = client1.get(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `force release by admin of another users lock succeeds`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val teamId = admin.loginAndCreateTeam("admin", "adminpass")
        val projectId = admin.createTestProject(teamId)

        // User2 joins team and acquires lock
        val user2 = jsonClient()
        user2.login("user2", "password2")
        inviteAndAccept(admin, user2, teamId)
        val acquireResponse = user2.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, acquireResponse.status)

        // Admin force-releases user2's lock
        val response = admin.delete(ApiRoutes.Projects.lock(projectId)) {
            parameter("force", true)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        // Lock should be gone — admin can now acquire
        val reacquire = admin.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.OK, reacquire.status)
        assertEquals("admin", reacquire.body<ProjectLockInfo>().username)
    }

    @Test
    fun `force release by regular collaborator returns 403`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("admin", "adminpass")
        val projectId = client1.createTestProject(teamId)

        // Admin acquires lock
        client1.post(ApiRoutes.Projects.lock(projectId))

        // Create user2 as COLLABORATOR
        val client2 = jsonClient()
        client2.login("user2", "password2")
        inviteAndAccept(client1, client2, teamId)

        // User2 tries force release — should fail
        val response = client2.delete(ApiRoutes.Projects.lock(projectId)) {
            parameter("force", true)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `acquire on non-existent project returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Projects.lock("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `acquire on project without team access returns 403`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("user1", "password1")
        val projectId = client1.createTestProject(teamId)

        // User2 is not in the team
        val client2 = jsonClient()
        client2.login("user2", "password2")

        val response = client2.post(ApiRoutes.Projects.lock(projectId))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `update project rejected when lock held by another user`() = testApplication {
        application { testModule() }
        val client1 = jsonClient()
        val teamId = client1.loginAndCreateTeam("user1", "password1")
        val projectId = client1.createTestProject(teamId)

        // User1 acquires lock
        client1.post(ApiRoutes.Projects.lock(projectId))

        // User2 joins team
        val client2 = jsonClient()
        client2.login("user2", "password2")
        inviteAndAccept(client1, client2, teamId)

        // User2 tries to update project — should be rejected
        val updateResponse = client2.put(ApiRoutes.Projects.byId(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Hijacked"))
        }
        assertEquals(HttpStatusCode.Conflict, updateResponse.status)
    }

    @Test
    fun `lock holder can update project successfully`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Acquire lock
        client.post(ApiRoutes.Projects.lock(projectId))

        // Update while holding lock — should succeed
        val updateResponse = client.put(ApiRoutes.Projects.byId(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Updated While Locked"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ProjectResponse>()
        assertEquals("Updated While Locked", updated.project.name)
    }

    @Test
    fun `delete project cascades to remove lock`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Acquire lock
        client.post(ApiRoutes.Projects.lock(projectId))

        // Delete project
        val deleteResponse = client.delete(ApiRoutes.Projects.byId(projectId))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Project is gone
        val getResponse = client.get(ApiRoutes.Projects.byId(projectId))
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `malformed project id returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Projects.lock("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val response = client.post(ApiRoutes.Projects.lock("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
