package com.github.mr3zee.e2e.releases

import androidx.compose.ui.test.*
import com.github.mr3zee.api.*
import com.github.mr3zee.createTestProjectWithBlocks
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test

/**
 * E2E tests for the unified Start Release screen.
 * Tests the full flow: navigate → pick project → configure name + params → start.
 */
@OptIn(ExperimentalTestApi::class)
class StartReleaseE2eTest : E2eTestBase() {

    @Test
    fun `start release via project picker navigates to start release screen`() = runComposeUiTest {
        loginAndApprove("sr-picker-user", "TestPass123")
        loginAndCreateTeamViaUi("sr-picker-user", "TestPass123", "SR Picker Team")

        // Get team ID for project creation
        directClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "sr-picker-user", password = "TestPass123"))
        }
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId

        // Create project with blocks
        val projectId = directClient.createTestProjectWithBlocks(teamId, "E2E Release Project")

        // Navigate to releases
        navigateToSection("sidebar_nav_releases", "release_list_screen")

        // Click FAB to open project picker
        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("start_release_form").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the project
        onNodeWithTag("project_dropdown").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_option_$projectId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_option_$projectId", useUnmergedTree = true).performClick()

        // Should navigate to start_release_screen
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("start_release_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("start_release_screen").assertExists()

        // Name should be pre-filled with project name
        onNodeWithTag("start_release_name").assertTextContains("E2E Release Project")
    }

    @Test
    fun `start release end-to-end from picker to release detail`() = runComposeUiTest {
        loginAndApprove("sr-e2e-user", "TestPass123")
        loginAndCreateTeamViaUi("sr-e2e-user", "TestPass123", "SR E2E Team")

        directClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "sr-e2e-user", password = "TestPass123"))
        }
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId

        val projectId = directClient.createTestProjectWithBlocks(teamId, "Full Flow Project")

        navigateToSection("sidebar_nav_releases", "release_list_screen")

        // Open picker → select project
        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("start_release_form").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_dropdown").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_option_$projectId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_option_$projectId", useUnmergedTree = true).performClick()

        // Wait for start release screen
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("start_release_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Click start
        onNodeWithTag("start_release_confirm").performClick()

        // Should navigate to release detail
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("release_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_detail_screen").assertExists()
    }

    @Test
    fun `start release with parameters from picker`() = runComposeUiTest {
        loginAndApprove("sr-params-user", "TestPass123")
        loginAndCreateTeamViaUi("sr-params-user", "TestPass123", "SR Params Team")

        directClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "sr-params-user", password = "TestPass123"))
        }
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId

        // Create project with a parameter that has empty value (requires filling)
        val projectResponse = directClient.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(
                name = "Param Project",
                teamId = teamId,
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = BlockId("b1"),
                            name = "Build",
                            type = BlockType.TEAMCITY_BUILD,
                        ),
                    ),
                ),
                parameters = listOf(
                    Parameter(key = "version", value = "1.0.0", label = "Version"),
                    Parameter(key = "env", value = "", label = "Environment"),
                ),
            ))
        }
        val projectId = projectResponse.body<ProjectResponse>().project.id.value

        navigateToSection("sidebar_nav_releases", "release_list_screen")

        // Open picker → select project
        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("start_release_form").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_dropdown").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_option_$projectId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_option_$projectId", useUnmergedTree = true).performClick()

        // Wait for start release screen with parameters
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("start_release_param_env").fetchSemanticsNodes().isNotEmpty()
        }

        // Start button should be disabled because env is empty
        onNodeWithTag("start_release_confirm").assertIsNotEnabled()

        // Fill in the empty parameter
        onNodeWithTag("start_release_param_env").performTextInput("production")
        waitForIdle()

        // Now start button should be enabled
        onNodeWithTag("start_release_confirm").assertIsEnabled()

        // Start the release
        onNodeWithTag("start_release_confirm").performClick()

        // Should navigate to release detail
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("release_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_detail_screen").assertExists()
    }
}
