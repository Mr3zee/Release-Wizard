package com.github.mr3zee.e2e

import com.github.mr3zee.AppJson
import com.github.mr3zee.CsrfTokenTestPlugin
import com.github.mr3zee.testDbConfig
import com.github.mr3zee.testModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.URLProtocol
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.netty.Netty
import io.ktor.serialization.kotlinx.json.json
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.AfterClass
import java.net.ServerSocket

/**
 * Abstract base class for all E2E tests.
 *
 * The embedded server starts once per test CLASS (via @BeforeClass) and is shared across
 * all test methods. This is required because PlatformUrl.jvm.kt uses a `lazy val` that
 * caches the server URL for the entire JVM lifetime. With `forkEvery = 1`, each test
 * class gets its own JVM, so the lazy val resets between classes.
 *
 * Each test method gets its own [directClient] (via @Before/@After) with a fresh cookie jar,
 * so login sessions don't leak between tests. Database state IS shared within a class
 * because the server runs continuously with the same H2 instance.
 */
abstract class E2eTestBase {
    protected lateinit var directClient: HttpClient

    companion object {
        @JvmStatic
        protected var port: Int = 0
        private var server: EmbeddedServer<*, *>? = null

        @JvmStatic
        @BeforeClass
        fun startServer() {
            port = ServerSocket(0).use { it.localPort }
            System.setProperty("release.wizard.server.url", "http://127.0.0.1:$port")

            val srv = embeddedServer(Netty, port = port) {
                testModule(dbConfig = testDbConfig())
            }
            srv.start(wait = false)
            server = srv

            waitForServerReady()
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
            server = null
            System.clearProperty("release.wizard.server.url")
        }

        private fun waitForServerReady() {
            var lastException: Exception? = null
            repeat(100) {
                try {
                    java.net.Socket("127.0.0.1", port).use { it.close() }
                    return
                } catch (e: Exception) {
                    lastException = e
                    Thread.sleep(100)
                }
            }
            error("Server did not become ready within 10 seconds. Last error: ${lastException?.message}")
        }
    }

    @Before
    fun setupClient() {
        val testPort = port
        val baseUrlPlugin = createClientPlugin("BaseUrl") {
            onRequest { request, _ ->
                request.url.protocol = URLProtocol.HTTP
                request.url.host = "127.0.0.1"
                request.url.port = testPort
            }
        }

        directClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            install(CsrfTokenTestPlugin)
            install(baseUrlPlugin)
        }
    }

    @After
    fun teardownClient() {
        directClient.close()
    }
}
