package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.navigation.parseUrlPath
import com.github.mr3zee.navigation.toUrlPath
import com.github.mr3zee.releases.StartReleaseScreen
import com.github.mr3zee.releases.StartReleaseViewModel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class StartReleaseScreenTest {

    private val projectId = ProjectId("p1")

    private val projectJsonNoParams = """{"project":{"id":"p1","name":"My Project","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"defaultTags":[],"createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"}}"""

    private val projectJsonWithParams = """{"project":{"id":"p1","name":"My Project","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[{"key":"version","value":"1.0.0","description":"Release version","label":"Version"},{"key":"env","value":"","description":"Target environment","label":"Environment"}],"defaultTags":[],"createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"}}"""

    private val projectJsonAllFilled = """{"project":{"id":"p1","name":"My Project","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[{"key":"version","value":"1.0.0","description":"","label":"Version"},{"key":"env","value":"prod","description":"","label":"Environment"}],"defaultTags":[],"createdAt":"2025-01-01T00:00:00Z","updatedAt":"2025-01-01T00:00:00Z"}}"""

    private val releaseResponseJson = """{"release":{"id":"r1","name":"My Project","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[]},"blockExecutions":[]}"""

    private fun makeViewModel(projectJson: String, releaseJson: String = releaseResponseJson): StartReleaseViewModel {
        val client = mockHttpClient(listOf(
            "/projects/p1" to json(projectJson),
            "/releases" to json(releaseJson, method = HttpMethod.Post),
        ))
        return StartReleaseViewModel(projectId, ProjectApiClient(client), ReleaseApiClient(client))
    }

    // ── Loading & Display ──

    @Test
    fun `screen shows loading initially`() = runComposeUiTest {
        // Use a client that returns 500 to slow things down
        val client = mockHttpClient(mapOf(
            "/projects/p1" to json("""{}""", status = HttpStatusCode.InternalServerError),
        ))
        val vm = StartReleaseViewModel(projectId, ProjectApiClient(client), ReleaseApiClient(client))
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }
        // Screen should exist
        onNodeWithTag("start_release_screen").assertExists()
    }

    @Test
    fun `name field pre-filled with project name`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonNoParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_name").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_name").assertTextContains("My Project")
    }

    @Test
    fun `parameters displayed with labels`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonWithParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_param_version").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_param_version").assertExists()
        onNodeWithTag("start_release_param_env").assertExists()
    }

    @Test
    fun `parameter with value is pre-filled`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonWithParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_param_version").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_param_version").assertTextContains("1.0.0")
    }

    // ── Validation ──

    @Test
    fun `start button disabled when empty param exists`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonWithParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        // env param is empty, so button should be disabled
        onNodeWithTag("start_release_confirm").assertIsNotEnabled()
    }

    @Test
    fun `start button enabled when all params filled`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonAllFilled)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_confirm").assertIsEnabled()
    }

    @Test
    fun `filling empty param enables start button`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonWithParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_param_env").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_confirm").assertIsNotEnabled()

        // Fill in the empty param
        onNodeWithTag("start_release_param_env").performTextInput("staging")
        waitForIdle()

        onNodeWithTag("start_release_confirm").assertIsEnabled()
    }

    @Test
    fun `start button disabled when no params and name cleared`() = runComposeUiTest {
        val vm = makeViewModel(projectJsonNoParams)
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_name").fetchSemanticsNodes().isNotEmpty()
        }
        // Initially enabled (name pre-filled, no params)
        onNodeWithTag("start_release_confirm").assertIsEnabled()

        // Clear the name
        onNodeWithTag("start_release_name").performTextClearance()
        waitForIdle()

        onNodeWithTag("start_release_confirm").assertIsNotEnabled()
    }

    // ── Navigation ──

    @Test
    fun `back button calls onBack`() = runComposeUiTest {
        var backCalled = false
        val vm = makeViewModel(projectJsonNoParams)
        setContent {
            MaterialTheme {
                StartReleaseScreen(vm, onBack = { backCalled = true }, onReleaseStarted = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_back").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_back").performClick()
        waitForIdle()

        assertEquals(true, backCalled)
    }

    @Test
    fun `starting release calls onReleaseStarted with ID`() = runComposeUiTest {
        var createdId: ReleaseId? = null
        val vm = makeViewModel(projectJsonNoParams)
        setContent {
            MaterialTheme {
                StartReleaseScreen(vm, onBack = {}, onReleaseStarted = { createdId = it })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { createdId != null }
        assertEquals(ReleaseId("r1"), createdId)
    }

    // ── Error handling ──

    @Test
    fun `shows error when project not found`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/projects/p1" to json("""{"error":"Not found"}""", status = HttpStatusCode.NotFound),
        ))
        val vm = StartReleaseViewModel(projectId, ProjectApiClient(client), ReleaseApiClient(client))
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_not_found").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("start_release_error").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `shows error when release creation fails`() = runComposeUiTest {
        val client = mockHttpClient(listOf(
            "/projects/p1" to json(projectJsonNoParams),
            "/releases" to json("""{"error":"Server error"}""", status = HttpStatusCode.InternalServerError, method = HttpMethod.Post),
        ))
        val vm = StartReleaseViewModel(projectId, ProjectApiClient(client), ReleaseApiClient(client))
        setContent { MaterialTheme { StartReleaseScreen(vm, onBack = {}, onReleaseStarted = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("start_release_error").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_error").assertExists()
    }

    // ── URL Routing ──

    @Test
    fun `round-trip StartRelease URL routing`() {
        val screen = Screen.StartRelease(ProjectId("abc"))
        assertEquals(screen, parseUrlPath(screen.toUrlPath()))
    }

    @Test
    fun `StartRelease URL format is correct`() {
        val screen = Screen.StartRelease(ProjectId("p1"))
        assertEquals("/projects/p1/start-release", screen.toUrlPath())
    }
}
