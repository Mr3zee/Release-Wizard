package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.TeamCityArtifactService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamCityArtifactServiceTest {

    private fun mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
    }

    @Test
    fun `flat artifact list with glob filtering`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/artifacts/children") && !url.contains("/artifacts/children/")) {
                respond(
                    """{"file":[
                        {"name":"app.jar","size":1024},
                        {"name":"readme.txt","size":256},
                        {"name":"utils.jar","size":512}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertEquals(listOf("app.jar", "utils.jar"), result)
    }

    @Test
    fun `nested directories with recursive traversal`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.endsWith("/artifacts/children") -> respond(
                    """{"file":[
                        {"name":"lib","children":{"href":"/lib"}},
                        {"name":"root.txt","size":100}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.endsWith("/artifacts/children/lib") -> respond(
                    """{"file":[
                        {"name":"app.jar","size":1024},
                        {"name":"deep","children":{"href":"/lib/deep"}}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.endsWith("/artifacts/children/lib/deep") -> respond(
                    """{"file":[
                        {"name":"nested.jar","size":256}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertEquals(listOf("lib/app.jar", "lib/deep/nested.jar"), result)
    }

    @Test
    fun `empty artifacts returns empty list`() = runBlocking {
        val client = mockClient { respond(
            """{"file":[]}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )}

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `no glob matches returns empty list`() = runBlocking {
        val client = mockClient { respond(
            """{"file":[{"name":"readme.txt","size":100},{"name":"config.xml","size":200}]}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )}

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `max depth limit stops recursion`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.endsWith("/artifacts/children") -> respond(
                    """{"file":[{"name":"level1","children":{"href":"/level1"}}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.endsWith("/artifacts/children/level1") -> respond(
                    """{"file":[{"name":"level2","children":{"href":"/level1/level2"}}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.endsWith("/artifacts/children/level1/level2") -> respond(
                    """{"file":[{"name":"deep.jar","size":100}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        val service = TeamCityArtifactService(client)

        // maxDepth=1 should prevent reaching level2/deep.jar
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 1,
            maxFiles = 1000,
        )

        assertTrue(result.isEmpty(), "Should not reach deep.jar with maxDepth=1, got: $result")
    }

    @Test
    fun `api error on sub-request returns partial results`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.endsWith("/artifacts/children") -> respond(
                    """{"file":[
                        {"name":"good.jar","size":100},
                        {"name":"subdir","children":{"href":"/subdir"}}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.endsWith("/artifacts/children/subdir") -> respond(
                    "Internal Server Error",
                    HttpStatusCode.InternalServerError,
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 1000,
        )

        assertEquals(listOf("good.jar"), result)
    }

    @Test
    fun `max files limit caps results`() = runBlocking {
        val client = mockClient { respond(
            """{"file":[
                {"name":"a.jar","size":100},
                {"name":"b.jar","size":200},
                {"name":"c.jar","size":300}
            ]}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )}

        val service = TeamCityArtifactService(client)
        val result = service.fetchMatchingArtifacts(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            buildId = "42",
            globPattern = "**/*.jar",
            maxDepth = 10,
            maxFiles = 2,
        )

        assertEquals(2, result.size)
    }
}
