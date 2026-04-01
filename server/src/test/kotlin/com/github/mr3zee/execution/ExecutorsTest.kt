package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.GitHubPublicationExecutor
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
import kotlin.test.assertFailsWith
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
        var capturedAuth: String? = null

        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            capturedAuth = request.headers["Authorization"]
            respond(
                """{"ok":true,"ts":"1234567890.123456","channel":"C0123456789"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val executor = SlackMessageExecutor(client)
        val outputs = executor.execute(
            block = slackBlock(),
            parameters = listOf(
                Parameter(key = "channel", value = "#releases"),
                Parameter(key = "text", value = "Deploy complete!"),
            ),
            context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
        )

        assertTrue(capturedUrl?.contains("chat.postMessage") == true)
        assertEquals("Bearer xoxb-test-token", capturedAuth)
        assertEquals("1234567890.123456", outputs["messageTs"])
        assertEquals("C0123456789", outputs["channel"])
        val body = capturedBody ?: error("capturedBody should have been set by the mock client")
        assertTrue(body.contains("Deploy complete!"))
        assertTrue(body.contains("#releases"))
    }

    @Test
    fun `slack executor throws on missing channel parameter`() = runBlocking {
        val client = mockClient { respond("ok", HttpStatusCode.OK) }

        val executor = SlackMessageExecutor(client)
        val e = assertFailsWith<IllegalArgumentException> {
            executor.execute(
                block = slackBlock(),
                parameters = listOf(Parameter(key = "text", value = "Hello")),
                context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
            )
        }
        val message = e.message ?: error("IllegalArgumentException should have a message")
        assertTrue(message.contains("channel"))
    }

    @Test
    fun `slack executor throws on missing text parameter`() = runBlocking {
        val client = mockClient { respond("ok", HttpStatusCode.OK) }
        val executor = SlackMessageExecutor(client)

        val e = assertFailsWith<IllegalArgumentException> {
            executor.execute(
                block = slackBlock(),
                parameters = listOf(Parameter(key = "channel", value = "#test")),
                context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
            )
        }
        val message = e.message ?: error("IllegalArgumentException should have a message")
        assertTrue(message.contains("text"))
    }

    @Test
    fun `slack executor throws on API failure`() = runBlocking {
        val client = mockClient {
            respond(
                """{"ok":false,"error":"channel_not_found"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val executor = SlackMessageExecutor(client)

        val e = assertFailsWith<RuntimeException> {
            executor.execute(
                block = slackBlock(),
                parameters = listOf(
                    Parameter(key = "channel", value = "#nonexistent"),
                    Parameter(key = "text", value = "Hello"),
                ),
                context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
            )
        }
        val message = e.message ?: error("RuntimeException should have a message")
        assertTrue(message.contains("Slack API error"))
    }

    @Test
    fun `slack executor resume finds message in history`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                url.contains("conversations.history") -> respond(
                    """{"ok":true,"messages":[{"text":"Deploy complete!","ts":"111.222"}]}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("""{"ok":true,"ts":"999.000","channel":"C0"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val executor = SlackMessageExecutor(client)
        val outputs = executor.resume(
            block = slackBlock(),
            parameters = listOf(
                Parameter(key = "channel", value = "#releases"),
                Parameter(key = "text", value = "Deploy complete!"),
            ),
            context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
        )

        // Should return the ts from history, not re-send
        assertEquals("111.222", outputs["messageTs"])
    }

    @Test
    fun `slack executor resume re-sends when message not in history`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                url.contains("conversations.history") -> respond(
                    """{"ok":true,"messages":[]}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("""{"ok":true,"ts":"999.000","channel":"C0"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }

        val executor = SlackMessageExecutor(client)
        val outputs = executor.resume(
            block = slackBlock(),
            parameters = listOf(
                Parameter(key = "channel", value = "#releases"),
                Parameter(key = "text", value = "Deploy complete!"),
            ),
            context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
        )

        // Should re-send and return ts from chat.postMessage
        assertEquals("999.000", outputs["messageTs"])
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
        val body = Json.decodeFromString<JsonObject>(capturedBody ?: error("capturedBody should have been set by the mock client"))
        assertEquals("v1.0", body["tag_name"]?.jsonPrimitive?.content)
        assertEquals("Release 1.0", body["name"]?.jsonPrimitive?.content)
        assertEquals("Changelog here", body["body"]?.jsonPrimitive?.content)
    }

    @Test
    fun `github publication executor throws on missing tagName`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val executor = GitHubPublicationExecutor(client)

        val e = assertFailsWith<IllegalArgumentException> {
            executor.execute(
                block = ghPubBlock(),
                parameters = emptyList(),
                context = context(
                    config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
                ),
            )
        }
        val message = e.message ?: error("IllegalArgumentException should have a message")
        assertTrue(message.contains("tagName"))
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

        val e = assertFailsWith<RuntimeException> {
            executor.execute(
                block = ghPubBlock(),
                parameters = listOf(Parameter(key = "tagName", value = "v1.0")),
                context = context(
                    config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
                ),
            )
        }
        val message = e.message ?: error("RuntimeException should have a message")
        assertTrue(message.contains("GitHub release creation failed"))
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

        val body = Json.decodeFromString<JsonObject>(capturedBody ?: error("capturedBody should have been set by the mock client"))
        assertEquals("v2.0", body["name"]?.jsonPrimitive?.content)
    }

    // --- Output keys match knownOutputs ---

    @Test
    fun `slack executor output keys are subset of knownOutputs`() = runBlocking {
        val client = mockClient {
            respond(
                """{"ok":true,"ts":"1234567890.123456","channel":"C0123456789"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val executor = SlackMessageExecutor(client)
        val outputs = executor.execute(
            block = slackBlock(),
            parameters = listOf(
                Parameter(key = "channel", value = "#test"),
                Parameter(key = "text", value = "test"),
            ),
            context = context(config = ConnectionConfig.SlackConfig(botToken = "xoxb-test-token")),
        )

        val knownNames = BlockType.SLACK_MESSAGE.knownOutputs().map { it.name }.toSet()
        val unknownKeys = outputs.keys - knownNames
        assertTrue(unknownKeys.isEmpty(), "Slack executor produced output keys not in knownOutputs: $unknownKeys")
    }

    @Test
    fun `github publication executor output keys are subset of knownOutputs`() = runBlocking {
        val client = mockClient {
            respond(
                """{"html_url":"https://github.com/o/r/releases/tag/v1","tag_name":"v1","id":1}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val executor = GitHubPublicationExecutor(client)
        val outputs = executor.execute(
            block = ghPubBlock(),
            parameters = listOf(Parameter(key = "tagName", value = "v1")),
            context = context(config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r")),
        )

        val knownNames = BlockType.GITHUB_PUBLICATION.knownOutputs().map { it.name }.toSet()
        val unknownKeys = outputs.keys - knownNames
        assertTrue(unknownKeys.isEmpty(), "GitHub Publication executor produced output keys not in knownOutputs: $unknownKeys")
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

        val e = assertFailsWith<IllegalStateException> {
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
        }
        val message = e.message ?: error("IllegalStateException should have a message")
        assertTrue(message.contains("requires a connection"))
    }
}
