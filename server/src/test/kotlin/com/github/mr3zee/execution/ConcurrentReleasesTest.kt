package com.github.mr3zee.execution

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.*
import com.github.mr3zee.testModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConcurrentReleasesTest {

    private suspend fun HttpClient.createTestProject(
        name: String = "Test Project",
        blocks: List<Block> = listOf(
            Block.ActionBlock(id = BlockId("block-a"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        ),
        edges: List<Edge> = emptyList(),
        parameters: List<Parameter> = emptyList(),
    ): ProjectTemplate {
        val response = post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProjectRequest(
                    name = name,
                    dagGraph = DagGraph(blocks = blocks, edges = edges),
                    parameters = parameters,
                )
            )
        }
        return response.body<ProjectResponse>().project
    }

    private suspend fun HttpClient.startAndAwaitRelease(
        projectTemplateId: ProjectId,
        parameters: List<Parameter> = emptyList(),
    ): ReleaseResponse {
        val startResponse = post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectTemplateId, parameters = parameters))
        }
        assertEquals(HttpStatusCode.Created, startResponse.status)
        val created = startResponse.body<ReleaseResponse>()

        val awaitResponse = post(ApiRoutes.Releases.await(created.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        return awaitResponse.body<ReleaseResponse>()
    }

    @Test
    fun `concurrent releases all complete successfully`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val n = 5

        // Create N different projects with unique configurations
        val projects = (1..n).map { i ->
            client.createTestProject(
                name = "Concurrent Project $i",
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("build-$i"),
                        name = "Build $i",
                        type = BlockType.TEAMCITY_BUILD,
                    ),
                ),
                parameters = listOf(Parameter(key = "version", value = "$i.0.0")),
            )
        }

        // Launch all releases concurrently and await all
        val results = coroutineScope {
            projects.mapIndexed { idx, project ->
                async {
                    client.startAndAwaitRelease(
                        projectTemplateId = project.id,
                        parameters = listOf(Parameter(key = "releaseIndex", value = "$idx")),
                    )
                }
            }.awaitAll()
        }

        // Verify all completed with SUCCEEDED
        assertEquals(n, results.size)
        for ((idx, result) in results.withIndex()) {
            assertEquals(
                ReleaseStatus.SUCCEEDED,
                result.release.status,
                "Release $idx should succeed",
            )
            assertTrue(
                result.blockExecutions.isNotEmpty(),
                "Release $idx should have block executions",
            )
            assertTrue(
                result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED },
                "All blocks in release $idx should succeed",
            )
        }

        // Verify no state leakage: each release has its own distinct block executions
        val allReleaseIds = results.map { it.release.id }.toSet()
        assertEquals(n, allReleaseIds.size, "All releases should have unique IDs")

        for (result in results) {
            assertTrue(
                result.blockExecutions.all { it.releaseId == result.release.id },
                "Block executions should belong to their own release",
            )
        }
    }

    @Test
    fun `concurrent releases with diamond DAGs all succeed`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val n = 5

        // Create N projects with diamond DAGs: A -> B, A -> C, B -> D, C -> D
        val projects = (1..n).map { i ->
            val blocks = listOf(
                Block.ActionBlock(id = BlockId("a-$i"), name = "A-$i", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b-$i"), name = "B-$i", type = BlockType.GITHUB_ACTION),
                Block.ActionBlock(id = BlockId("c-$i"), name = "C-$i", type = BlockType.SLACK_MESSAGE),
                Block.ActionBlock(id = BlockId("d-$i"), name = "D-$i", type = BlockType.GITHUB_PUBLICATION),
            )
            val edges = listOf(
                Edge(fromBlockId = BlockId("a-$i"), toBlockId = BlockId("b-$i")),
                Edge(fromBlockId = BlockId("a-$i"), toBlockId = BlockId("c-$i")),
                Edge(fromBlockId = BlockId("b-$i"), toBlockId = BlockId("d-$i")),
                Edge(fromBlockId = BlockId("c-$i"), toBlockId = BlockId("d-$i")),
            )
            client.createTestProject(
                name = "Diamond Project $i",
                blocks = blocks,
                edges = edges,
            )
        }

        val results = coroutineScope {
            projects.map { project ->
                async { client.startAndAwaitRelease(project.id) }
            }.awaitAll()
        }

        for ((idx, result) in results.withIndex()) {
            assertEquals(
                ReleaseStatus.SUCCEEDED,
                result.release.status,
                "Diamond release $idx should succeed",
            )
            assertEquals(
                4,
                result.blockExecutions.size,
                "Diamond release $idx should have 4 block executions",
            )
            assertTrue(
                result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED },
                "All blocks in diamond release $idx should succeed",
            )
        }
    }

    @Test
    fun `concurrent releases have isolated parameters`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val n = 5

        // Use a single project template for all releases, but different parameters
        val project = client.createTestProject(
            name = "Shared Project",
            blocks = listOf(
                Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            parameters = listOf(Parameter(key = "env", value = "staging")),
        )

        val results = coroutineScope {
            (1..n).map { i ->
                async {
                    client.startAndAwaitRelease(
                        projectTemplateId = project.id,
                        parameters = listOf(
                            Parameter(key = "version", value = "$i.0.0"),
                            Parameter(key = "buildNumber", value = "$i"),
                        ),
                    )
                }
            }.awaitAll()
        }

        // All should succeed
        for (result in results) {
            assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        }

        // Each release should have its own parameters (not leaking from others)
        val versionValues = results.map { result ->
            result.release.parameters.find { it.key == "version" }?.value
        }.toSet()
        assertEquals(n, versionValues.size, "Each release should have its own unique version parameter")

        // Verify the env parameter from the project template is preserved in all
        for (result in results) {
            assertEquals(
                "staging",
                result.release.parameters.find { it.key == "env" }?.value,
                "Project-level env parameter should be preserved",
            )
        }
    }

    @Test
    fun `concurrent releases with sequential chains all succeed`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val n = 5

        // Create projects with 3-block chains: A -> B -> C
        val projects = (1..n).map { i ->
            val blocks = listOf(
                Block.ActionBlock(id = BlockId("a-$i"), name = "A-$i", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b-$i"), name = "B-$i", type = BlockType.GITHUB_ACTION),
                Block.ActionBlock(id = BlockId("c-$i"), name = "C-$i", type = BlockType.SLACK_MESSAGE),
            )
            val edges = listOf(
                Edge(fromBlockId = BlockId("a-$i"), toBlockId = BlockId("b-$i")),
                Edge(fromBlockId = BlockId("b-$i"), toBlockId = BlockId("c-$i")),
            )
            client.createTestProject(
                name = "Chain Project $i",
                blocks = blocks,
                edges = edges,
            )
        }

        val results = coroutineScope {
            projects.map { project ->
                async { client.startAndAwaitRelease(project.id) }
            }.awaitAll()
        }

        for ((idx, result) in results.withIndex()) {
            assertEquals(
                ReleaseStatus.SUCCEEDED,
                result.release.status,
                "Chain release $idx should succeed",
            )
            assertEquals(
                3,
                result.blockExecutions.size,
                "Chain release $idx should have 3 block executions",
            )
            assertTrue(
                result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED },
                "All blocks in chain release $idx should succeed",
            )
        }

        // Verify block execution ordering within each release
        for (result in results) {
            val blocksByBlockId = result.blockExecutions.associateBy { it.blockId.value }
            // Find the chain blocks by suffix pattern
            val aBlock = blocksByBlockId.values.find { it.blockId.value.startsWith("a-") }
                ?: error("Block execution starting with 'a-' should exist")
            val bBlock = blocksByBlockId.values.find { it.blockId.value.startsWith("b-") }
                ?: error("Block execution starting with 'b-' should exist")
            val cBlock = blocksByBlockId.values.find { it.blockId.value.startsWith("c-") }
                ?: error("Block execution starting with 'c-' should exist")

            val bStartedAt = bBlock.startedAt ?: error("bBlock.startedAt should not be null")
            val aFinishedAt = aBlock.finishedAt ?: error("aBlock.finishedAt should not be null")
            val cStartedAt = cBlock.startedAt ?: error("cBlock.startedAt should not be null")
            val bFinishedAt = bBlock.finishedAt ?: error("bBlock.finishedAt should not be null")
            assertTrue(bStartedAt >= aFinishedAt, "B should start after A finishes")
            assertTrue(cStartedAt >= bFinishedAt, "C should start after B finishes")
        }
    }
}
