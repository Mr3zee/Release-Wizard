package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.GitHubPublicationExecutor
import com.github.mr3zee.execution.executors.MavenCentralExecutor
import com.github.mr3zee.execution.executors.SlackMessageExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutorsTest {

    private fun mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
    }

    private fun slackBlock(connectionId: String = "conn-1") = Block.ActionBlock(
        id = BlockId("slack-1"),
        name = "Send Slack",
        type = BlockType.SLACK_MESSAGE,
        connectionId = ConnectionId(connectionId),
    )

    private fun ghPubBlock(connectionId: String = "conn-1") = Block.ActionBlock(
        id = BlockId("gh-pub-1"),
        name = "Create Release",
        type = BlockType.GITHUB_PUBLICATION,
        connectionId = ConnectionId(connectionId),
    )

    private fun context(connectionId: String = "conn-1", config: ConnectionConfig) = ExecutionContext(
        releaseId = ReleaseId("release-1"),
        parameters = emptyList(),
        blockOutputs = emptyMap(),
        connections = mapOf(ConnectionId(connectionId) to config),
    )

    // --- Slack Message Executor ---

    @Test
    fun `slack executor sends message and returns outputs`() = runBlocking {
        var capturedBody: String? = null
        var capturedUrl: String? = null

        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond("ok", HttpStatusCode.OK)
        }

        val executor = SlackMessageExecutor(client)
        val outputs = executor.execute(
            block = slackBlock(),
            parameters = listOf(
                Parameter(key = "text", value = "Deploy complete!"),
                Parameter(key = "channel", value = "#releases"),
            ),
            context = context(config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test")),
        )

        assertEquals("https://hooks.slack.com/test", capturedUrl)
        assertEquals("sent", outputs["messageTs"])
        assertEquals("#releases", outputs["channel"])
        // todo claude: proper null handling
        assertTrue(capturedBody!!.contains("Deploy complete!"))
    }

    @Test
    fun `slack executor uses default channel when not specified`() = runBlocking {
        val client = mockClient { respond("ok", HttpStatusCode.OK) }

        val executor = SlackMessageExecutor(client)
        val outputs = executor.execute(
            block = slackBlock(),
            parameters = listOf(Parameter(key = "text", value = "Hello")),
            context = context(config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test")),
        )

        assertEquals("default", outputs["channel"])
    }

    @Test
    fun `slack executor throws on missing text parameter`() = runBlocking {
        val client = mockClient { respond("ok", HttpStatusCode.OK) }
        val executor = SlackMessageExecutor(client)

        try {
            executor.execute(
                block = slackBlock(),
                parameters = emptyList(),
                context = context(config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test")),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("text"))
        }
    }

    @Test
    fun `slack executor throws on API failure`() = runBlocking {
        val client = mockClient { respond("channel_not_found", HttpStatusCode.NotFound) }
        val executor = SlackMessageExecutor(client)

        try {
            executor.execute(
                block = slackBlock(),
                parameters = listOf(Parameter(key = "text", value = "Hello")),
                context = context(config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test")),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: RuntimeException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("Slack webhook failed"))
        }
    }

    // --- GitHub Publication Executor ---

    @Test
    fun `github publication executor creates release and returns outputs`() = runBlocking {
        var capturedAuth: String? = null
        var capturedUrl: String? = null
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedAuth = request.headers["Authorization"]
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"html_url":"https://github.com/test/repo/releases/tag/v1.0","tag_name":"v1.0","id":123}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val executor = GitHubPublicationExecutor(client)
        val outputs = executor.execute(
            block = ghPubBlock(),
            parameters = listOf(
                Parameter(key = "tagName", value = "v1.0"),
                Parameter(key = "releaseName", value = "Release 1.0"),
                Parameter(key = "body", value = "Changelog here"),
            ),
            context = context(
                config = ConnectionConfig.GitHubConfig(
                    token = "ghp_test_token",
                    owner = "test",
                    repo = "repo",
                ),
            ),
        )

        assertEquals("https://api.github.com/repos/test/repo/releases", capturedUrl)
        assertEquals("Bearer ghp_test_token", capturedAuth)
        assertEquals("https://github.com/test/repo/releases/tag/v1.0", outputs["releaseUrl"])
        assertEquals("v1.0", outputs["tagName"])

        // Verify request body
        // todo claude: proper null handling
        val body = Json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("v1.0", body["tag_name"]?.jsonPrimitive?.content)
        assertEquals("Release 1.0", body["name"]?.jsonPrimitive?.content)
        assertEquals("Changelog here", body["body"]?.jsonPrimitive?.content)
    }

    @Test
    fun `github publication executor throws on missing tagName`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val executor = GitHubPublicationExecutor(client)

        try {
            executor.execute(
                block = ghPubBlock(),
                parameters = emptyList(),
                context = context(
                    config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
                ),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("tagName"))
        }
    }

    @Test
    fun `github publication executor throws on API failure`() = runBlocking {
        val client = mockClient {
            respond(
                """{"message":"Not Found"}""",
                HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val executor = GitHubPublicationExecutor(client)

        try {
            executor.execute(
                block = ghPubBlock(),
                parameters = listOf(Parameter(key = "tagName", value = "v1.0")),
                context = context(
                    config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
                ),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: RuntimeException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("GitHub release creation failed"))
        }
    }

    @Test
    fun `github publication executor uses tagName as release name when not provided`() = runBlocking {
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                """{"html_url":"https://github.com/o/r/releases/tag/v2.0","tag_name":"v2.0","id":456}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val executor = GitHubPublicationExecutor(client)
        executor.execute(
            block = ghPubBlock(),
            parameters = listOf(Parameter(key = "tagName", value = "v2.0")),
            context = context(
                config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
            ),
        )

        // todo claude: proper null handling
        val body = Json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("v2.0", body["name"]?.jsonPrimitive?.content)
    }

    // --- Maven Central Executor ---

    private fun mavenBlock(connectionId: String = "conn-1") = Block.ActionBlock(
        id = BlockId("maven-1"),
        name = "Check Maven",
        type = BlockType.MAVEN_CENTRAL_PUBLICATION,
        connectionId = ConnectionId(connectionId),
    )

    private val mavenConfig = ConnectionConfig.MavenCentralConfig(
        username = "user",
        password = "pass",
        baseUrl = "https://central.sonatype.com",
    )

    @Test
    fun `maven executor checks deployment by ID`() = runBlocking {
        var capturedUrl: String? = null

        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                """{"deploymentState":"PUBLISHED","deploymentId":"deploy-123"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val executor = MavenCentralExecutor(client)
        val outputs = executor.execute(
            block = mavenBlock(),
            parameters = listOf(Parameter(key = "deploymentId", value = "deploy-123")),
            context = context(config = mavenConfig),
        )

        // todo claude: proper null handling
        assertTrue(capturedUrl!!.contains("/api/v1/publisher/deployment/deploy-123"))
        assertEquals("deploy-123", outputs["repositoryId"])
        assertEquals("PUBLISHED", outputs["status"])
    }

    @Test
    fun `maven executor checks published by coordinates`() = runBlocking {
        var capturedUrl: String? = null

        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                """{"published":"true"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val executor = MavenCentralExecutor(client)
        val outputs = executor.execute(
            block = mavenBlock(),
            parameters = listOf(
                Parameter(key = "groupId", value = "com.example"),
                Parameter(key = "artifactId", value = "mylib"),
                Parameter(key = "version", value = "1.0.0"),
            ),
            context = context(config = mavenConfig),
        )

        // todo claude: proper null handling
        assertTrue(capturedUrl!!.contains("/api/v1/publisher/published"))
        assertEquals("com.example:mylib:1.0.0", outputs["repositoryId"])
        assertEquals("PUBLISHED", outputs["status"])
    }

    @Test
    fun `maven executor throws on missing parameters`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val executor = MavenCentralExecutor(client)

        try {
            executor.execute(
                block = mavenBlock(),
                parameters = emptyList(),
                context = context(config = mavenConfig),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("deploymentId"))
        }
    }

    // --- Missing connection tests ---

    @Test
    fun `executor throws when connection ID is missing`() = runBlocking {
        val client = mockClient { respond("ok", HttpStatusCode.OK) }
        val executor = SlackMessageExecutor(client)

        val blockWithoutConnection = Block.ActionBlock(
            id = BlockId("slack-no-conn"),
            name = "No Connection",
            type = BlockType.SLACK_MESSAGE,
            connectionId = null,
        )

        try {
            executor.execute(
                block = blockWithoutConnection,
                parameters = listOf(Parameter(key = "text", value = "test")),
                context = ExecutionContext(
                    releaseId = ReleaseId("r1"),
                    parameters = emptyList(),
                    blockOutputs = emptyMap(),
                    connections = emptyMap(),
                ),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalStateException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("requires a connection"))
        }
    }
}
