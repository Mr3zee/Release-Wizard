package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertTrue

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
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = {}, onLogout = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
        onNodeWithText("My Pipeline").assertExists()
        onNodeWithText("Release Flow").assertExists()
        onNodeWithText("1 blocks").assertExists()
        onNodeWithText("0 blocks").assertExists()
    }

    @Test
    fun `shows empty state`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = {}, onLogout = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No projects yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No projects yet. Create one to get started.").assertExists()
    }

    @Test
    fun `shows error state on API failure`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("Internal server error", HttpStatusCode.InternalServerError)))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = {}, onLogout = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `has connections and logout buttons`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = {}, onLogout = {})
            }
        }

        onNodeWithTag("connections_button").assertExists()
        onNodeWithTag("logout_button").assertExists()
        onNodeWithTag("create_project_fab").assertExists()
    }

    @Test
    fun `connections button triggers callback`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")))
        var clicked = false
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = { clicked = true }, onLogout = {})
            }
        }

        onNodeWithTag("connections_button").performClick()
        assertTrue(clicked)
    }

    @Test
    fun `fab opens create project dialog`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient("""{"projects":[]}""")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = {}, onConnections = {}, onLogout = {})
            }
        }

        onNodeWithTag("create_project_fab").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_name_input").assertExists()
        onNodeWithText("New Project").assertExists()
    }

    @Test
    fun `clicking project item triggers edit callback`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()))
        var editedId: ProjectId? = null
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onCreateProject = {}, onEditProject = { editedId = it }, onConnections = {}, onLogout = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_item_p1").performClick()
        assertTrue(editedId?.value == "p1")
    }
}
