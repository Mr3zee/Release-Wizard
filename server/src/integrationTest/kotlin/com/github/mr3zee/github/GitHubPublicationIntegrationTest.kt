@file:Suppress("FunctionName")

package com.github.mr3zee.github

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.executors.GitHubPublicationExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.*
import org.junit.Assert.fail
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubPublicationIntegrationTest {

    companion object {
        private var config: GitHubTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = GitHubTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createGitHubTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    private val releasesToCleanup = mutableListOf<Pair<Long, String>>()

    @After
    fun cleanupReleases() = runBlocking {
        val c = client ?: return@runBlocking
        val cfg = config ?: return@runBlocking
        for ((releaseId, tagName) in releasesToCleanup) {
            try {
                c.deleteGitHubRelease(cfg, releaseId)
            } catch (_: Exception) {
            }
            try {
                c.deleteGitHubTag(cfg, tagName)
            } catch (_: Exception) {
            }
        }
        releasesToCleanup.clear()
    }

    private fun uniqueTag() = "test-v${System.currentTimeMillis()}"

    private fun block() = Block.ActionBlock(
        id = BlockId("gh-pub-integ"),
        name = "Integration Release",
        type = BlockType.GITHUB_PUBLICATION,
        connectionId = ConnectionId("conn-1"),
    )

    private fun context(): ExecutionContext {
        val cfg = config ?: error("GitHubTestConfig not loaded — setUp should have skipped this test")
        return ExecutionContext(
            releaseId = ReleaseId("integ-release-1"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-1") to ConnectionConfig.GitHubConfig(
                    token = cfg.token,
                    owner = cfg.owner,
                    repo = cfg.repo,
                )
            ),
        )
    }

    private fun trackRelease(outputs: Map<String, String>) {
        val tag = outputs["tagName"] ?: return
        val id = outputs["releaseId"]?.toLongOrNull() ?: return
        releasesToCleanup.add(id to tag)
    }

    @Test
    fun `execute creates release and returns outputs`() = runBlocking {
        val tag = uniqueTag()
        val executor = GitHubPublicationExecutor(client ?: error("HttpClient not initialized"))

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(Parameter(key = "tagName", value = tag)),
            context = context(),
        )

        trackRelease(outputs)

        val releaseUrl = outputs["releaseUrl"] ?: error("Expected 'releaseUrl' in executor outputs")
        assertTrue(releaseUrl.isNotEmpty(), "releaseUrl should not be empty")
        assertTrue(releaseUrl.contains("github.com"), "releaseUrl should contain github.com")
        assertEquals(tag, outputs["tagName"])
    }

    @Test
    fun `resume returns existing release`() = runBlocking {
        val tag = uniqueTag()
        val executor = GitHubPublicationExecutor(client ?: error("HttpClient not initialized"))
        val params = listOf(Parameter(key = "tagName", value = tag))
        val ctx = context()

        // Create via execute
        val outputs1 = executor.execute(block = block(), parameters = params, context = ctx)
        trackRelease(outputs1)

        // Resume should find existing release
        val outputs2 = executor.resume(block = block(), parameters = params, context = ctx)

        assertEquals(outputs1["releaseUrl"], outputs2["releaseUrl"])
        assertEquals(outputs1["tagName"], outputs2["tagName"])
    }

    @Test
    fun `execute with all optional params`() = runBlocking {
        val tag = uniqueTag()
        val executor = GitHubPublicationExecutor(client ?: error("HttpClient not initialized"))

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(
                Parameter(key = "tagName", value = tag),
                Parameter(key = "releaseName", value = "Test Release $tag"),
                Parameter(key = "body", value = "Integration test release body"),
                Parameter(key = "draft", value = "true"),
                Parameter(key = "prerelease", value = "true"),
            ),
            context = context(),
        )

        // Track for cleanup using releaseId from executor output (works for drafts too)
        trackRelease(outputs)

        // Verify via raw GET that fields match
        // Note: draft releases may not be findable via /releases/tags/ endpoint,
        // but cleanup is already tracked above via releaseId from executor output.
        val release = (client ?: error("HttpClient not initialized")).findGitHubReleaseByTag(config ?: error("GitHubTestConfig not loaded"), tag)
        if (release != null) {
            assertEquals(true, release["draft"]?.jsonPrimitive?.boolean)
            assertEquals(true, release["prerelease"]?.jsonPrimitive?.boolean)
            assertEquals("Integration test release body", release["body"]?.jsonPrimitive?.content)
            assertEquals("Test Release $tag", release["name"]?.jsonPrimitive?.content)
        }

        assertNotNull(outputs["releaseId"], "releaseId should be present in outputs")
        assertEquals(tag, outputs["tagName"])
    }

    @Test
    fun `execute with invalid token throws`() = runBlocking {
        val tag = uniqueTag()
        val executor = GitHubPublicationExecutor(client ?: error("HttpClient not initialized"))

        val invalidContext = ExecutionContext(
            releaseId = ReleaseId("integ-release-1"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-1") to ConnectionConfig.GitHubConfig(
                    token = "ghp_invalid_token_000000000000000000",
                    owner = (config ?: error("GitHubTestConfig not loaded")).owner,
                    repo = (config ?: error("GitHubTestConfig not loaded")).repo,
                )
            ),
        )

        try {
            executor.execute(
                block = block(),
                parameters = listOf(Parameter(key = "tagName", value = tag)),
                context = invalidContext,
            )
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            val msg = e.message ?: error("RuntimeException should have a message")
            assertTrue(msg.contains("GitHub release creation failed"), "Message: $msg")
        }
    }
}
