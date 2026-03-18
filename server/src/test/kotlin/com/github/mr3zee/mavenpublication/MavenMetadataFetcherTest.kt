package com.github.mr3zee.mavenpublication

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import com.github.mr3zee.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MavenMetadataFetcherTest {

    private fun fetcher(handler: MockRequestHandler): MavenMetadataFetcher =
        MavenMetadataFetcher(HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(AppJson) }
        })

    @Test
    fun `parses versions from valid metadata XML`() = runTest {
        val xml = """
            <?xml version="1.0"?>
            <metadata>
              <versioning>
                <versions>
                  <version>1.0.0</version>
                  <version>1.1.0</version>
                  <version>2.0.0</version>
                </versions>
              </versioning>
            </metadata>
        """.trimIndent()

        val f = fetcher {
            respond(xml, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/xml"))
        }

        val result = f.fetch("https://repo.example.com/maven2", "com.example", "my-lib")
        assertEquals(setOf("1.0.0", "1.1.0", "2.0.0"), result)
    }

    @Test
    fun `returns null on 404`() = runTest {
        val f = fetcher { respond("Not Found", HttpStatusCode.NotFound) }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "missing")
        assertNull(result)
    }

    @Test
    fun `returns null on malformed XML`() = runTest {
        val f = fetcher { respond("this is not xml <<< !!", HttpStatusCode.OK) }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "lib")
        assertNull(result)
    }

    @Test
    fun `returns empty set when versions element is empty`() = runTest {
        val xml = """<?xml version="1.0"?><metadata><versioning><versions></versions></versioning></metadata>"""
        val f = fetcher { respond(xml, HttpStatusCode.OK) }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "lib")
        assertTrue(result != null && result.isEmpty())
    }

    @Test
    fun `builds correct metadata URL from groupId with dots`() = runTest {
        var capturedUrl = ""
        val xml = """<?xml version="1.0"?><metadata><versioning><versions><version>1.0.0</version></versions></versioning></metadata>"""
        val f = fetcher { request ->
            capturedUrl = request.url.toString()
            respond(xml, HttpStatusCode.OK)
        }
        f.fetch("https://repo.example.com/maven2", "com.example.foo", "my-lib")
        assertTrue(capturedUrl.contains("com/example/foo/my-lib/maven-metadata.xml"), "Expected path with dots replaced: $capturedUrl")
    }

    @Test
    fun `returns null on network error`() = runTest {
        val f = fetcher { throw Exception("Connection refused") }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "lib")
        assertNull(result)
    }

    @Test
    fun `returns null on server error`() = runTest {
        val f = fetcher { respond("Internal Error", HttpStatusCode.InternalServerError) }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "lib")
        assertNull(result)
    }

    @Test
    fun `ignores top-level version element, returns only versioning list entries`() = runTest {
        // Some snapshot metadata has a top-level <version> alongside <versioning>/<versions>
        val xml = """
            <?xml version="1.0"?>
            <metadata>
              <version>1.0.0</version>
              <versioning>
                <versions>
                  <version>1.0.0</version>
                  <version>1.1.0</version>
                </versions>
              </versioning>
            </metadata>
        """.trimIndent()
        val f = fetcher { respond(xml, HttpStatusCode.OK) }
        val result = f.fetch("https://repo.example.com/maven2", "com.example", "lib")
        // Should return exactly {1.0.0, 1.1.0} — top-level <version> must not be duplicated
        assertEquals(setOf("1.0.0", "1.1.0"), result)
    }

    @Test
    fun `repoUrl with trailing slash produces correct path`() = runTest {
        var capturedUrl = ""
        val xml = """<?xml version="1.0"?><metadata><versioning><versions><version>1.0.0</version></versions></versioning></metadata>"""
        val f = fetcher { request ->
            capturedUrl = request.url.toString()
            respond(xml, HttpStatusCode.OK)
        }
        f.fetch("https://repo.example.com/maven2/", "com.example", "lib")
        assertTrue(!capturedUrl.contains("//com"), "Should not have double slash: $capturedUrl")
        assertTrue(capturedUrl.contains("maven2/com/example/lib/maven-metadata.xml"), "Expected clean path: $capturedUrl")
    }
}
