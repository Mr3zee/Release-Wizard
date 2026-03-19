package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProjectListScreenTest {

    private val projectsJson = """{"projects":[
        {"id":"p1","name":"My Pipeline","description":"A test pipeline","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"},
        {"id":"p2","name":"Release Flow","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
    ]}"""

    private fun projectClient(json: String = projectsJson, status: HttpStatusCode = HttpStatusCode.OK) = mockHttpClient(
        mapOf("/projects" to json(json, status))
    )

    @Test
    fun `shows projects from API`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
        onNodeWithText("My Pipeline").assertExists()
        onNodeWithText("Release Flow").assertExists()
        onNodeWithText("1 block").assertExists()
        onNodeWithText("0 blocks").assertExists()
    }

    @Test
    fun `shows empty state`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No projects yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No projects yet. Create one to get started.").assertExists()
    }

    @Test
    fun `shows error state on API failure`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("Internal server error", HttpStatusCode.InternalServerError)), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `has create project fab`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        onNodeWithTag("create_project_fab").assertExists()
    }

    @Test
    fun `fab opens create project dialog`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        onNodeWithTag("create_project_fab").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_name_input").assertExists()
        onNodeWithText("New Project").assertExists()
    }

    @Test
    fun `clicking project item triggers edit callback`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("test-team")))
        var editedId: ProjectId? = null
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = { editedId = it })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_item_p1", useUnmergedTree = true).performClick()
        assertEquals(ProjectId("p1"), editedId)
    }

    @Test
    fun `delete button opens confirmation dialog`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        // Open the per-item overflow menu, then click Delete
        onNodeWithTag("project_menu_p1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_menu_item").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_menu_item").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_project_confirm_p1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_project_confirm_p1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `delete confirmation dialog cancel dismisses`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        // Open the per-item overflow menu, then click Delete
        onNodeWithTag("project_menu_p1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_menu_item").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_menu_item").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_project_confirm_p1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_project_confirm_p1_cancel", useUnmergedTree = true).performClick()
        onNodeWithTag("delete_project_confirm_p1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `refresh button exists`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        onNodeWithTag("refresh_button").assertExists()
    }

    @Test
    fun `create project then navigates to edit`() = runComposeUiTest {
        val createdProjectJson = """{"project":{"id":"new-p1","name":"New Project","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val createdListJson = """{"projects":[{"id":"new-p1","name":"New Project","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]}"""

        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when (request.method) {
                HttpMethod.Get if path.endsWith("/projects") ->
                    respond(createdListJson, status = HttpStatusCode.OK, headers = jsonHeaders)

                HttpMethod.Post if path.endsWith("/projects") ->
                    respond(createdProjectJson, status = HttpStatusCode.Created, headers = jsonHeaders)

                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }

        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))
        var editedId: ProjectId? = null

        setContent {
            MaterialTheme {
                ProjectListScreen(
                    viewModel = vm,
                    onEditProject = { editedId = it },
                )
            }
        }

        // Wait for initial load (empty list or populated list)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_project_fab").fetchSemanticsNodes().isNotEmpty()
        }

        // Open create dialog
        onNodeWithTag("create_project_fab").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Type project name and click Create
        onNodeWithTag("project_name_input").performTextInput("New Project")
        onNodeWithText("Create").performClick()

        // Wait for the onEditProject callback to fire
        waitUntil(timeoutMillis = 5000L) { editedId != null }
        assertEquals(ProjectId("new-p1"), editedId)
    }

    // ---- Refresh Tests ----

    @Test
    fun `refresh icon button exists in top bar`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        onNodeWithTag("refresh_button").assertExists()
    }

    @Test
    fun `refresh menu item triggers re-fetch`() = runComposeUiTest {
        val returnNewData = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/projects") -> {
                    val projects = if (!returnNewData.get()) {
                        """[{"id":"p1","name":"Alpha","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]"""
                    } else {
                        """[{"id":"p1","name":"Alpha","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"},{"id":"p-new","name":"Beta New","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]"""
                    }
                    respond("""{"projects":$projects}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Beta New").assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Beta New").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Beta New").assertExists()
    }

    @Test
    fun `refresh error shows banner and keeps list visible`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/projects") -> {
                    if (!failOnRefresh.get()) {
                        respond(projectsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }

        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        onNodeWithText("My Pipeline").assertExists()
    }

    @Test
    fun `dismiss refresh error hides banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/projects") -> {
                    if (!failOnRefresh.get()) {
                        respond(projectsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }
}
