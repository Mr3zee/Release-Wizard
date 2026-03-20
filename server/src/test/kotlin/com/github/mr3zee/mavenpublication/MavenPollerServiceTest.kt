package com.github.mr3zee.mavenpublication

import com.github.mr3zee.api.ApproveBlockRequest
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MavenPollerServiceTest {

    private val projectId = ProjectId("00000000-0000-0000-0000-000000000001")

    private fun trigger(
        id: String = "00000000-0000-0000-0000-000000000002",
        includeSnapshots: Boolean = false,
    ) = MavenTrigger(
        id = id,
        projectId = projectId,
        repoUrl = "https://repo.example.com",
        groupId = "com.example",
        artifactId = "lib",
        parameterKey = "version",
        enabled = true,
        includeSnapshots = includeSnapshots,
        lastCheckedAt = null,
    )

    private fun entry(t: MavenTrigger, versions: Set<String> = emptySet()) =
        MavenTriggerWithVersions(trigger = t, knownVersions = versions)

    @Test
    fun `fires release for each new version`() = runTest {
        val t = trigger()
        val fired = mutableListOf<Pair<ProjectId, List<Parameter>>>()

        val poller = MavenPollerService(
            repository = stubRepo(listOf(entry(t, setOf("1.0.0")))),
            fetcher = fetcherReturning(setOf("1.0.0", "1.1.0", "1.2.0")),
            releasesService = releasesCapturing(fired),
        )
        poller.pollAllTriggers()

        assertEquals(2, fired.size)
        assertEquals(setOf("1.1.0", "1.2.0"), fired.map { it.second.first().value }.toSet())
    }

    @Test
    fun `does not fire when no new versions`() = runTest {
        val t = trigger()
        val fired = mutableListOf<Pair<ProjectId, List<Parameter>>>()

        val poller = MavenPollerService(
            repository = stubRepo(listOf(entry(t, setOf("1.0.0", "1.1.0")))),
            fetcher = fetcherReturning(setOf("1.0.0", "1.1.0")),
            releasesService = releasesCapturing(fired),
        )
        poller.pollAllTriggers()

        assertTrue(fired.isEmpty())
    }

    @Test
    fun `skips snapshots when includeSnapshots is false`() = runTest {
        val t = trigger(includeSnapshots = false)
        val fired = mutableListOf<Pair<ProjectId, List<Parameter>>>()

        val poller = MavenPollerService(
            repository = stubRepo(listOf(entry(t, setOf("1.0.0")))),
            fetcher = fetcherReturning(setOf("1.0.0", "1.1.0", "1.2.0-SNAPSHOT")),
            releasesService = releasesCapturing(fired),
        )
        poller.pollAllTriggers()

        assertEquals(1, fired.size)
        assertEquals("1.1.0", fired.first().second.first().value)
    }

    @Test
    fun `includes snapshots when includeSnapshots is true`() = runTest {
        val t = trigger(includeSnapshots = true)
        val fired = mutableListOf<Pair<ProjectId, List<Parameter>>>()

        val poller = MavenPollerService(
            repository = stubRepo(listOf(entry(t, setOf("1.0.0")))),
            fetcher = fetcherReturning(setOf("1.0.0", "1.1.0-SNAPSHOT")),
            releasesService = releasesCapturing(fired),
        )
        poller.pollAllTriggers()

        assertEquals(1, fired.size)
        assertEquals("1.1.0-SNAPSHOT", fired.first().second.first().value)
    }

    @Test
    fun `fetcher returning null skips trigger without updating knownVersions`() = runTest {
        val t = trigger()
        val updated = mutableListOf<Set<String>>()

        val poller = MavenPollerService(
            repository = object : StubRepo(listOf(entry(t, setOf("1.0.0")))) {
                override suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant) {
                    updated += versions
                }
            },
            fetcher = fetcherReturning(null),
            releasesService = releasesCapturing(mutableListOf()),
        )
        poller.pollAllTriggers()

        assertTrue(updated.isEmpty())
    }

    @Test
    fun `when versions unchanged, updateKnownVersions still called to refresh lastCheckedAt`() = runTest {
        val t = trigger()
        val knownVersions = setOf("1.0.0", "1.1.0")
        val updatedCalls = mutableListOf<Set<String>>()

        val repo = object : StubRepo(listOf(entry(t, knownVersions))) {
            override suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant) {
                updatedCalls += versions
            }
        }
        val poller = MavenPollerService(
            repository = repo,
            fetcher = fetcherReturning(knownVersions), // same versions as known
            releasesService = releasesCapturing(mutableListOf()),
        )
        poller.pollAllTriggers()

        // Must be called to update lastCheckedAt even when no new versions
        assertEquals(1, updatedCalls.size)
        assertEquals(knownVersions, updatedCalls.first())
    }

    @Test
    fun `partial fire failure - only successful versions added to knownVersions`() = runTest {
        val t = trigger()
        val updated = mutableListOf<Set<String>>()

        val poller = MavenPollerService(
            repository = object : StubRepo(listOf(entry(t, setOf("1.0.0")))) {
                override suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant) {
                    updated += versions
                }
            },
            fetcher = fetcherReturning(setOf("1.0.0", "1.1.0", "1.2.0")),
            releasesService = object : StubReleasesService() {
                override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release {
                    if (parameters.first().value == "1.1.0") throw RuntimeException("Simulated failure for 1.1.0")
                    return stubRelease()
                }
            },
        )
        poller.pollAllTriggers()

        assertEquals(1, updated.size)
        assertEquals(setOf("1.0.0", "1.2.0"), updated.first()) // 1.1.0 excluded (failed)
    }

    // --- Helpers ---

    private fun stubRepo(entries: List<MavenTriggerWithVersions>): MavenTriggerRepository =
        StubRepo(entries)

    private fun fetcherReturning(versions: Set<String>?): MavenMetadataFetcher {
        val engine = MockEngine { _ ->
            if (versions == null) throw Exception("Simulated network failure")
            val xml = buildXml(versions)
            respond(xml, HttpStatusCode.OK)
        }
        return MavenMetadataFetcher(HttpClient(engine))
    }

    private fun buildXml(versions: Set<String>): String =
        """<?xml version="1.0"?><metadata><versioning><versions>${
            versions.joinToString("") { "<version>$it</version>" }
        }</versions></versioning></metadata>"""

    private fun releasesCapturing(fired: MutableList<Pair<ProjectId, List<Parameter>>>): ReleasesService =
        object : StubReleasesService() {
            override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release {
                fired += projectId to parameters
                return stubRelease()
            }
        }

    private fun stubRelease() = Release(
        id = ReleaseId("00000000-0000-0000-0000-000000000099"),
        projectTemplateId = projectId,
        status = ReleaseStatus.RUNNING,
        dagSnapshot = DagGraph(),
        parameters = emptyList(),
    )

    open class StubRepo(private val entries: List<MavenTriggerWithVersions>) : MavenTriggerRepository {
        override suspend fun findAllEnabled() = entries
        override suspend fun updateKnownVersions(id: String, versions: Set<String>, checkedAt: Instant) = Unit
        override suspend fun findByProjectId(projectId: ProjectId) = entries.map { it.trigger }
        override suspend fun findById(id: String) = entries.firstOrNull { it.trigger.id == id }?.trigger
        override suspend fun create(projectId: ProjectId, repoUrl: String, groupId: String, artifactId: String, parameterKey: String, includeSnapshots: Boolean, enabled: Boolean, knownVersions: Set<String>, createdBy: String): MavenTrigger = error("Not expected in poll tests")
        override suspend fun updateEnabled(id: String, enabled: Boolean): MavenTrigger = error("Not expected in poll tests")
        override suspend fun delete(id: String): Boolean = error("Not expected in poll tests")
    }

    open class StubReleasesService : ReleasesService {
        override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release = error("override me")
        override suspend fun listReleases(session: UserSession, teamId: TeamId?, offset: Int, limit: Int, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, tag: String?): Pair<List<Release>, Long> = error("stub")
        override suspend fun getRelease(id: ReleaseId, session: UserSession): Release? = error("stub")
        override suspend fun getBlockExecutions(releaseId: ReleaseId, session: UserSession): List<BlockExecution> = error("stub")
        override suspend fun startRelease(request: CreateReleaseRequest, session: UserSession): Release = error("stub")
        override suspend fun rerunRelease(id: ReleaseId, session: UserSession): Release = error("stub")
        override suspend fun cancelRelease(id: ReleaseId, session: UserSession): Boolean = error("stub")
        override suspend fun archiveRelease(id: ReleaseId, session: UserSession): Boolean = error("stub")
        override suspend fun deleteRelease(id: ReleaseId, session: UserSession): Boolean = error("stub")
        override suspend fun awaitRelease(id: ReleaseId): Unit = error("stub")
        override suspend fun stopBlock(releaseId: ReleaseId, blockId: BlockId, session: UserSession): Boolean = error("stub")
        override suspend fun stopRelease(id: ReleaseId, session: UserSession): Boolean = error("stub")
        override suspend fun resumeRelease(id: ReleaseId, session: UserSession): Boolean = error("stub")
        override suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId, session: UserSession): Boolean = error("stub")
        override suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest, session: UserSession): Boolean = error("stub")
        override suspend fun checkAccess(id: ReleaseId, session: UserSession): Unit = error("stub")
    }
}
