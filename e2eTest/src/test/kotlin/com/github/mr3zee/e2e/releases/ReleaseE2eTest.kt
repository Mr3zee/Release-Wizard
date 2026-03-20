package com.github.mr3zee.e2e.releases

import androidx.compose.ui.test.*
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.UserInfo
import com.github.mr3zee.createTestProjectWithBlocks
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReleaseE2eTest : E2eTestBase() {

    @Test
    fun `navigate to release list`() = runComposeUiTest {
        directClient.login("rel-nav-user", "TestPass123")

        loginAndCreateTeamViaUi("rel-nav-user", "TestPass123", "Release Team")
        navigateToSection("sidebar_nav_releases", "release_list_screen")

        onNodeWithTag("release_list_screen").assertExists()
    }

    @Test
    fun `start release from UI`() = runComposeUiTest {
        directClient.login("rel-start-user", "TestPass123")

        loginAndCreateTeamViaUi("rel-start-user", "TestPass123", "Start Release Team")

        // Re-login directClient to get fresh session with team info
        directClient.login("rel-start-user", "TestPass123")
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId

        // Create project with blocks via directClient (avoids DAG editor navigation)
        val projectId = directClient.createTestProjectWithBlocks(teamId, "Release Source Project")

        // Navigate to releases
        navigateToSection("sidebar_nav_releases", "release_list_screen")

        // Click start release
        onNodeWithTag("start_release_fab").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("start_release_form").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the project using its testTag
        onNodeWithTag("project_dropdown").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_option_$projectId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_option_$projectId", useUnmergedTree = true).performClick()
        waitForIdle()

        // Start the release
        onNodeWithTag("start_release_confirm").performClick()

        // Should navigate to release detail
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("release_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_detail_screen").assertExists()
    }
}
