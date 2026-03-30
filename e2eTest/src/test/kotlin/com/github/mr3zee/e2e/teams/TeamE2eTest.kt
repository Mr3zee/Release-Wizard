package com.github.mr3zee.e2e.teams

import androidx.compose.ui.test.*
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.LoginRequest
import com.github.mr3zee.api.RegisterRequest
import com.github.mr3zee.api.UserInfo
import com.github.mr3zee.e2e.E2eTestBase
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TeamCreateE2eTest : E2eTestBase() {

    @Test
    fun `create team through UI`() = runComposeUiTest {
        loginAndApprove("team-create-user", "TestPass123")

        loginViaUi("team-create-user", "TestPass123")

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("create_team_fab").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_name_input").performTextInput("My E2E Team")
        waitForIdle()
        onNodeWithTag("create_team_confirm").performClick()

        // Team creation now navigates to team detail
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class TeamDetailE2eTest : E2eTestBase() {

    @Test
    fun `view team detail and audit log`() = runComposeUiTest {
        loginAndApprove("detail-user", "TestPass123")

        loginAndCreateTeamViaUi("detail-user", "TestPass123", "Detail Team")
        navigateToSection("sidebar_nav_teams", "team_list_screen")

        // Get team ID from server to click the correct team item by testTag
        // (text-based click doesn't propagate through merged semantic tree)
        directClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "detail-user", password = "TestPass123"))
        }
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId.value

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_item_$teamId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_item_$teamId", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_detail_screen").assertExists()

        // Navigate to audit log
        onNodeWithTag("audit_log_button", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("audit_log_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("audit_log_screen").assertExists()
    }
}

@OptIn(ExperimentalTestApi::class)
class TeamInviteE2eTest : E2eTestBase() {

    @Test
    fun `invite user to team via manage screen`() = runComposeUiTest {
        loginAndApprove("invite-leader", "TestPass123")

        // Register the invite target (without logging in as them)
        directClient.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "invite-target", password = "TestPass123"))
        }

        loginAndCreateTeamViaUi("invite-leader", "TestPass123", "Invite Team")
        navigateToSection("sidebar_nav_teams", "team_list_screen")

        // Re-login directClient to get fresh session with updated teams
        directClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "invite-leader", password = "TestPass123"))
        }
        val userInfo = directClient.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val teamId = userInfo.teams.first().teamId.value

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_item_$teamId", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_item_$teamId", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("manage_team_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_manage_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for invite button to appear (members section loads asynchronously)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("invite_user_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("invite_user_button", useUnmergedTree = true).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("invite_user_id_input").performTextInput("invite-target")
        waitForIdle()
        onNodeWithTag("invite_user_confirm").performClick()

        // Verify invite was sent (the invited username should appear in pending invites)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithText("invite-target", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
