package com.github.mr3zee.e2e

import androidx.compose.ui.test.*
import com.github.mr3zee.App
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
            // Phase 1: wait for TCP port to open
            var lastException: Exception? = null
            repeat(100) {
                try {
                    java.net.Socket("127.0.0.1", port).use { it.close() }
                    lastException = null
                    // Phase 2: wait for HTTP to respond (ensures Ktor pipeline is initialized)
                    val url = java.net.URI("http://127.0.0.1:$port/health").toURL()
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    conn.responseCode // triggers the request
                    conn.disconnect()
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

    /**
     * Helper: renders App(), waits for login screen, logs in with given credentials,
     * and waits for navigation past the login screen.
     * Ends on either `project_list_screen` or `team_list_screen`.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun ComposeUiTest.loginViaUi(username: String, password: String) {
        setContent { App() }

        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("login_username").performTextInput(username)
        onNodeWithTag("login_password").performTextInput(password)
        waitForIdle()
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Helper: logs in and creates a team through the UI, ending on `project_list_screen`
     * with activeTeamId properly set. Use this for tests that need team context
     * (projects, connections, releases).
     *
     * The login response doesn't include teams, so activeTeamId is null after login.
     * Creating a team through the UI triggers onRefreshUser + onTeamChanged, which
     * properly sets the active team and navigates to project list.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun ComposeUiTest.loginAndCreateTeamViaUi(
        username: String,
        password: String,
        teamName: String,
    ) {
        loginViaUi(username, password)

        // After login, user lands on team_list (login response has no teams)
        if (onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()) {
            // Create team through UI — this sets activeTeamId and navigates to project_list
            onNodeWithTag("create_team_fab").performClick()
            waitForIdle()
            waitUntil(timeoutMillis = 5_000L) {
                onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("team_name_input").performTextInput(teamName)
            waitForIdle()
            onNodeWithTag("create_team_confirm").performClick()

            waitUntil(timeoutMillis = 15_000L) {
                onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Helper: navigates to a top-level section via sidebar click.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun ComposeUiTest.navigateToSection(sidebarTag: String, screenTag: String) {
        onNodeWithTag(sidebarTag, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag(screenTag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Helper: after login, the user lands on team_list_screen (login response has no teams).
     * This selects a team by clicking it, waits for team detail, then navigates back to teams.
     * After this, the sidebar team switcher has the active team set.
     */
    @OptIn(ExperimentalTestApi::class)
    protected fun ComposeUiTest.selectTeamFromList(teamName: String) {
        // Make sure we're on the team list
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        // Click on the team
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithText(teamName).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(teamName).performClick()

        // Wait for team detail
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("team_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Go back to team list via back button
        onNodeWithTag("back_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

