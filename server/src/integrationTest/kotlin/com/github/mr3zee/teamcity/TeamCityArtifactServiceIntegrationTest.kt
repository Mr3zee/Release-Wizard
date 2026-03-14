@file:Suppress("FunctionName")

package com.github.mr3zee.teamcity

import com.github.mr3zee.execution.executors.TeamCityArtifactService
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [TeamCityArtifactService] against a real TeamCity instance.
 *
 * Requires `teamcity.test.artifactBuildId` (or env `TEAMCITY_TEST_ARTIFACT_BUILD_ID`)
 * pointing to a completed build that has published artifacts.
 */
class TeamCityArtifactServiceIntegrationTest {

    companion object {
        private var config: TeamCityTestConfig? = null
        private var artifactBuildId: String? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = TeamCityTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            artifactBuildId = (config ?: error("TeamCityTestConfig not loaded")).artifactBuildId
            Assume.assumeNotNull(artifactBuildId)
            client = createTeamCityTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    @Test
    fun `broad glob returns non-empty artifact list`() = runBlocking {
        // todo claude: duplicate 11 lines
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val service = TeamCityArtifactService(client ?: error("HttpClient not initialized"))

        val artifacts = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertTrue(artifacts.isNotEmpty(), "Build $artifactBuildId should have artifacts")
    }

    @Test
    fun `specific glob pattern filters correctly`() = runBlocking {
        // todo claude: duplicate 12 lines
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val service = TeamCityArtifactService(client ?: error("HttpClient not initialized"))

        // Fetch all artifacts first to find a real extension to filter by
        val all = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*",
            maxDepth = 10,
            maxFiles = 1000,
        )
        assertTrue(all.isNotEmpty(), "Precondition: build must have artifacts")

        // Pick the extension of the first artifact and filter by it
        val firstExt = all.first().substringAfterLast('.', "")
        Assume.assumeTrue("First artifact must have an extension", firstExt.isNotEmpty())

        val filtered = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*.$firstExt",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertTrue(filtered.isNotEmpty(), "Should have at least one .$firstExt artifact")
        assertTrue(
            filtered.all { it.endsWith(".$firstExt") },
            "All results should end with .$firstExt: $filtered",
        )
        assertTrue(
            filtered.size <= all.size,
            "Filtered set ($filtered) should be subset of all ($all)",
        )
    }

    @Test
    fun `maxFiles limits returned results`() = runBlocking {
        // todo claude: duplicate 11 lines
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val service = TeamCityArtifactService(client ?: error("HttpClient not initialized"))

        val all = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*",
            maxDepth = 10,
            maxFiles = 1000,
        )
        Assume.assumeTrue("Build must have at least 2 artifacts", all.size >= 2)

        val limited = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*",
            maxDepth = 10,
            maxFiles = 1,
        )

        assertEquals(1, limited.size, "maxFiles=1 should return exactly 1 artifact")
        assertTrue(all.contains(limited.first()), "Limited result should be in the full set")
    }

    @Test
    fun `non-matching glob returns empty list`() = runBlocking {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val service = TeamCityArtifactService(client ?: error("HttpClient not initialized"))

        val result = service.fetchMatchingArtifacts(
            serverUrl = cfg.serverUrl,
            token = cfg.token,
            buildId = artifactBuildId ?: error("artifactBuildId not configured"),
            globPattern = "**/*.nonexistent_extension_xyz",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertTrue(result.isEmpty(), "No artifacts should match .nonexistent_extension_xyz")
    }
}
