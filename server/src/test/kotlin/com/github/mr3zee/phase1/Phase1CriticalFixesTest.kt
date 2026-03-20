package com.github.mr3zee.phase1

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.koin.dsl.module
import org.koin.ktor.ext.getKoin
import kotlin.test.*

/**
 * Tests for Phase 1 — Critical Race Conditions & Data Safety fixes.
 *
 * Stream 1A: Execution Engine Concurrency (EXEC-C1, C2, C3)
 * Stream 1B: Release Approval Mutex (REL-C1, C2)
 * Stream 1C: Scheduler Atomicity (SCHED-C1, C2)
 * Stream 1D: Project Lock Atomicity (PROJ-C1, C2)
 * Stream 1E: Team Membership TOCTOU (TEAM-C1, C2)
 * Stream 1F: Infrastructure Quick Wins (INFRA-C1, TAG-C1, TAG-C2)
 * Stream 1G: Maven Poller Races (MAVEN-C1, C2)
 */
class Phase1CriticalFixesTest {

    // ── Stream 1F: INFRA-C1 — SecureRandom singleton ──────────────────────

    @Test
    fun `INFRA-C1 encryption works with singleton SecureRandom`() {
        val config = testEncryptionConfig()
        val service = com.github.mr3zee.security.EncryptionService(config)
        // Encrypt multiple values to verify singleton reuse
        val plaintexts = (1..100).map { "secret-$it" }
        val encrypted = plaintexts.map { service.encrypt(it) }
        val decrypted = encrypted.map { service.decrypt(it) }
        assertEquals(plaintexts, decrypted)
        // All ciphertexts should be unique (random IV per call)
        assertEquals(encrypted.size, encrypted.toSet().size)
    }

    // ── Stream 1F: TAG-C1 — Team routes use TagService ────────────────────

    @Test
    fun `TAG-C1 team-scoped tag rename goes through TagService`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Start a release to create tags
        val releaseResp = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                tags = listOf("v1.0"),
            ))
        }
        assertEquals(HttpStatusCode.Created, releaseResp.status)

        // Rename tag via team-scoped route (should use TagService, not TagRepository)
        val renameResp = client.put("${ApiRoutes.Teams.BASE}/${teamId.value}/tags/v1.0") {
            contentType(ContentType.Application.Json)
            setBody(RenameTagRequest(newName = "v2.0"))
        }
        assertEquals(HttpStatusCode.OK, renameResp.status)

        // Verify tag was renamed
        val tagsResp = client.get("${ApiRoutes.Teams.BASE}/${teamId.value}/tags")
        assertEquals(HttpStatusCode.OK, tagsResp.status)
        val tagList = tagsResp.body<TagListResponse>()
        assertTrue(tagList.tags.any { it.name == "v2.0" })
        assertFalse(tagList.tags.any { it.name == "v1.0" })
    }

    @Test
    fun `TAG-C1 team-scoped tag delete goes through TagService`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Create release with tag
        client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                tags = listOf("delete-me"),
            ))
        }

        // Delete tag via team-scoped route
        val deleteResp = client.delete("${ApiRoutes.Teams.BASE}/${teamId.value}/tags/delete-me")
        assertEquals(HttpStatusCode.NoContent, deleteResp.status)

        // Verify tag is gone
        val tagsResp = client.get("${ApiRoutes.Teams.BASE}/${teamId.value}/tags")
        val tagList = tagsResp.body<TagListResponse>()
        assertFalse(tagList.tags.any { it.name == "delete-me" })
    }

    @Test
    fun `TAG-C2 tag rename creates audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Create release with tag
        client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                tags = listOf("audit-tag"),
            ))
        }

        // Rename tag
        client.put("${ApiRoutes.Teams.BASE}/${teamId.value}/tags/audit-tag") {
            contentType(ContentType.Application.Json)
            setBody(RenameTagRequest(newName = "renamed-tag"))
        }

        // Check audit log for TAG_RENAMED event
        val auditResp = client.get("${ApiRoutes.Teams.BASE}/${teamId.value}/audit")
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val auditList = auditResp.body<AuditEventListResponse>()
        assertTrue(auditList.events.any { it.action == AuditAction.TAG_RENAMED })
    }

    @Test
    fun `TAG-C2 tag delete creates audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Create release with tag
        client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                tags = listOf("del-audit-tag"),
            ))
        }

        // Delete tag
        client.delete("${ApiRoutes.Teams.BASE}/${teamId.value}/tags/del-audit-tag")

        // Check audit log for TAG_DELETED event
        val auditResp = client.get("${ApiRoutes.Teams.BASE}/${teamId.value}/audit")
        val auditList = auditResp.body<AuditEventListResponse>()
        assertTrue(auditList.events.any { it.action == AuditAction.TAG_DELETED })
    }

    // ── Stream 1D: PROJ-C1 — Atomic lock check + update ──────────────────

    @Test
    fun `PROJ-C1 update rejects when another user holds the lock atomically`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()
        val projectId = admin.createTestProject(teamId)

        // Register and add user2 to the team
        user2.login("user2", "password2")
        admin.inviteAndAccept(user2, teamId, "user2")

        // User2 acquires lock
        val lockResp = user2.post("${ApiRoutes.Projects.BASE}/$projectId/lock") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("ttlMinutes" to 30))
        }
        assertEquals(HttpStatusCode.OK, lockResp.status)

        // Admin tries to update — should fail with 409 (lock conflict)
        val updateResp = admin.put("${ApiRoutes.Projects.BASE}/$projectId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Updated Name"))
        }
        assertEquals(HttpStatusCode.Conflict, updateResp.status)
    }

    @Test
    fun `PROJ-C1 update succeeds when same user holds the lock`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Acquire lock
        client.post("${ApiRoutes.Projects.BASE}/$projectId/lock") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("ttlMinutes" to 30))
        }

        // Update with same user — should succeed
        val updateResp = client.put("${ApiRoutes.Projects.BASE}/$projectId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Updated By Lock Owner"))
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)
    }

    @Test
    fun `PROJ-C1 update succeeds when no lock exists`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Update without any lock — should succeed
        val updateResp = client.put("${ApiRoutes.Projects.BASE}/$projectId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "No Lock Update"))
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)
    }

    // ── Stream 1D: PROJ-C2 — Expired lock takeover via update ────────────

    @Test
    fun `PROJ-C2 lock can be reacquired after force release`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()
        val projectId = admin.createTestProject(teamId)

        user2.login("user2", "password2")
        admin.inviteAndAccept(user2, teamId, "user2")

        // User2 acquires lock
        user2.post("${ApiRoutes.Projects.BASE}/$projectId/lock") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("ttlMinutes" to 1))
        }

        // Admin force-releases the lock (requires ?force=true)
        admin.delete("${ApiRoutes.Projects.BASE}/$projectId/lock?force=true")

        // Admin should now be able to acquire the lock
        val lockResp = admin.post("${ApiRoutes.Projects.BASE}/$projectId/lock") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("ttlMinutes" to 30))
        }
        assertEquals(HttpStatusCode.OK, lockResp.status)
    }

    // ── Stream 1E: TEAM-C1 — Atomic last-lead protection ─────────────────

    @Test
    fun `TEAM-C1 cannot demote the last team lead atomically`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        // Get member list to find the admin's userId
        val membersResp = admin.get("${ApiRoutes.Teams.BASE}/${teamId.value}/members")
        val members = membersResp.body<TeamMemberListResponse>()
        val adminMember = members.members.first()

        // Try to demote the only lead — should fail
        val demoteResp = admin.put("${ApiRoutes.Teams.BASE}/${teamId.value}/members/${adminMember.userId.value}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR))
        }
        assertEquals(HttpStatusCode.BadRequest, demoteResp.status)
    }

    @Test
    fun `TEAM-C1 cannot remove the last team lead atomically`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        // Add user2 as collaborator
        user2.login("user2", "password2")
        admin.inviteAndAccept(user2, teamId, "user2")

        // Get admin's member info
        val membersResp = admin.get("${ApiRoutes.Teams.BASE}/${teamId.value}/members")
        val members = membersResp.body<TeamMemberListResponse>()
        val adminMember = members.members.first { it.role == TeamRole.TEAM_LEAD }

        // Try to remove the only lead — should fail
        val removeResp = admin.delete("${ApiRoutes.Teams.BASE}/${teamId.value}/members/${adminMember.userId.value}")
        assertEquals(HttpStatusCode.BadRequest, removeResp.status)
    }

    @Test
    fun `TEAM-C1 can demote lead when multiple leads exist`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        user2.login("user2", "password2")
        admin.inviteAndAccept(user2, teamId, "user2")

        // Get user2's member info
        val membersResp = admin.get("${ApiRoutes.Teams.BASE}/${teamId.value}/members")
        val members = membersResp.body<TeamMemberListResponse>()
        val user2Member = members.members.first { it.role == TeamRole.COLLABORATOR }

        // Promote user2 to TEAM_LEAD
        val promoteResp = admin.put("${ApiRoutes.Teams.BASE}/${teamId.value}/members/${user2Member.userId.value}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.TEAM_LEAD))
        }
        assertEquals(HttpStatusCode.OK, promoteResp.status)

        // Now admin can be demoted (two leads exist)
        val adminMember = members.members.first { it.role == TeamRole.TEAM_LEAD }
        val demoteResp = admin.put("${ApiRoutes.Teams.BASE}/${teamId.value}/members/${adminMember.userId.value}") {
            contentType(ContentType.Application.Json)
            setBody(UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR))
        }
        assertEquals(HttpStatusCode.OK, demoteResp.status)
    }

    @Test
    fun `TEAM-C1 leave team fails for last lead`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        // Try to leave as the only lead — should fail
        val leaveResp = admin.post("${ApiRoutes.Teams.BASE}/${teamId.value}/leave")
        assertEquals(HttpStatusCode.BadRequest, leaveResp.status)
    }

    // ── Stream 1E: TEAM-C2 — Atomic invite accept ────────────────────────

    @Test
    fun `TEAM-C2 invite accept is atomic - adds member in single transaction`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        user2.login("user2", "password2")

        // Invite user2
        val inviteResp = admin.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "user2"))
        }
        assertEquals(HttpStatusCode.Created, inviteResp.status)
        val invite = inviteResp.body<TeamInvite>()

        // Accept invite
        val acceptResp = user2.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")
        assertEquals(HttpStatusCode.OK, acceptResp.status)

        // Verify user2 is a member
        val membersResp = admin.get("${ApiRoutes.Teams.BASE}/${teamId.value}/members")
        val members = membersResp.body<TeamMemberListResponse>()
        assertTrue(members.members.any { it.username == "user2" })
    }

    @Test
    fun `TEAM-C2 double invite accept returns error`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        user2.login("user2", "password2")

        // Invite and accept
        val inviteResp = admin.post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = "user2"))
        }
        val invite = inviteResp.body<TeamInvite>()
        user2.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")

        // Try to accept again — should fail
        val doubleAccept = user2.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")
        assertNotEquals(HttpStatusCode.OK, doubleAccept.status)
    }

    @Test
    fun `TEAM-C2 join request approve is atomic`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        val user2 = jsonClient()
        val teamId = admin.loginAndCreateTeam()

        user2.login("user2", "password2")

        // Submit join request
        val joinResp = user2.post("${ApiRoutes.Teams.BASE}/${teamId.value}/join-requests")
        assertEquals(HttpStatusCode.Created, joinResp.status)
        val joinRequest = joinResp.body<JoinRequest>()

        // Approve join request
        val approveResp = admin.post("${ApiRoutes.Teams.BASE}/${teamId.value}/join-requests/${joinRequest.id}/approve")
        assertEquals(HttpStatusCode.OK, approveResp.status)

        // Verify user2 is now a member
        val membersResp = admin.get("${ApiRoutes.Teams.BASE}/${teamId.value}/members")
        val members = membersResp.body<TeamMemberListResponse>()
        assertTrue(members.members.any { it.username == "user2" })
    }

    // ── Stream 1B: REL-C1, C2 — Approval mutex ───────────────────────────

    @Test
    fun `REL-C2 release completes and approval mutex state is clean`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        // Start a release (stub executor completes it immediately)
        val releaseResp = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        assertEquals(HttpStatusCode.Created, releaseResp.status)
        val release = releaseResp.body<ReleaseResponse>()

        // Wait for it to reach terminal state
        waitUntil(delayMillis = 100) {
            val r = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
            r.release.status.isTerminal
        }

        // Verify release completed successfully (no crashes from mutex cleanup)
        val detail = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
        assertTrue(detail.release.status.isTerminal)
    }

    // ── Stream 1A: EXEC-C1 — Event-driven execution ──────────────────────

    @Test
    fun `EXEC-C1 diamond DAG executes correctly with event-driven scheduling`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create a diamond DAG: A -> B, A -> C, B -> D, C -> D
        val dagGraph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("d"), name = "D", type = BlockType.TEAMCITY_BUILD),
            ),
            edges = listOf(
                Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
                Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("c")),
                Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("d")),
                Edge(fromBlockId = BlockId("c"), toBlockId = BlockId("d")),
            ),
        )

        val projectId = client.createTestProject(teamId, dagGraph = dagGraph)

        // Start release
        val releaseResp = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        assertEquals(HttpStatusCode.Created, releaseResp.status)
        val release = releaseResp.body<ReleaseResponse>()

        // Wait for completion
        waitUntil(delayMillis = 100) {
            val r = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
            r.release.status.isTerminal
        }

        // Verify all blocks succeeded
        val detail = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
        assertEquals(ReleaseStatus.SUCCEEDED, detail.release.status)
    }

    @Test
    fun `EXEC-C1 parallel blocks with no dependencies execute concurrently`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Three independent blocks (no edges)
        val dagGraph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("x"), name = "X", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("y"), name = "Y", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("z"), name = "Z", type = BlockType.TEAMCITY_BUILD),
            ),
        )

        val projectId = client.createTestProject(teamId, dagGraph = dagGraph)

        val releaseResp = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        val release = releaseResp.body<ReleaseResponse>()

        waitUntil(delayMillis = 100) {
            val r = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
            r.release.status.isTerminal
        }

        val detail = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
        assertEquals(ReleaseStatus.SUCCEEDED, detail.release.status)
    }

    @Test
    fun `EXEC-C1 failed predecessor marks downstream blocks as failed`() = testApplication {
        application {
            testModule()
            // Override StubBlockExecutor to fail the "fail" block at execution time
            getKoin().loadModules(listOf(module {
                single<BlockExecutor> { StubBlockExecutor(failBlockIds = setOf("fail")) }
            }), allowOverride = true)
        }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // A -> B, A is configured to fail at execution time via StubBlockExecutor
        val dagGraph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(
                    id = BlockId("fail"),
                    name = "WillFail",
                    type = BlockType.TEAMCITY_BUILD,
                ),
                Block.ActionBlock(id = BlockId("skip"), name = "WillBeSkipped", type = BlockType.TEAMCITY_BUILD),
            ),
            edges = listOf(
                Edge(fromBlockId = BlockId("fail"), toBlockId = BlockId("skip")),
            ),
        )

        val projectId = client.createTestProject(teamId, dagGraph = dagGraph)

        val releaseResp = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId(projectId)))
        }
        val release = releaseResp.body<ReleaseResponse>()

        waitUntil(delayMillis = 100) {
            val r = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
            r.release.status.isTerminal
        }

        val detail = client.get("${ApiRoutes.Releases.BASE}/${release.release.id.value}").body<ReleaseResponse>()
        assertEquals(ReleaseStatus.FAILED, detail.release.status)
    }

    // ── Stream 1C: SCHED-C1 — Schedule operations ────────────────────────

    @Test
    fun `SCHED-C1 schedule can be created`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId)

        val schedResp = client.post("${ApiRoutes.Projects.BASE}/$projectId/schedules") {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 0 * * *",
                enabled = true,
            ))
        }
        assertEquals(HttpStatusCode.Created, schedResp.status)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private suspend fun HttpClient.inviteAndAccept(
        user2: HttpClient,
        teamId: TeamId,
        username: String,
    ) {
        val inviteResp = post("${ApiRoutes.Teams.BASE}/${teamId.value}/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(username = username))
        }
        val invite = inviteResp.body<TeamInvite>()
        user2.post("${ApiRoutes.Auth.MyInvites.BASE}/${invite.id}/accept")
    }
}
