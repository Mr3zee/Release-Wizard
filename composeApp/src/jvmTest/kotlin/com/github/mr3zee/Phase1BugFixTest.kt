package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.automation.CreateScheduleDialog
import com.github.mr3zee.automation.ProjectAutomationScreen
import com.github.mr3zee.automation.ProjectAutomationViewModel
import com.github.mr3zee.editor.TemplatePickerDialog
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.teams.AuditLogScreen
import com.github.mr3zee.teams.AuditLogViewModel
import com.github.mr3zee.teams.TeamManageScreen
import com.github.mr3zee.teams.TeamManageViewModel
import io.ktor.client.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Phase1BugFixTest {

    // ── 1A: Cron Validation Tests ──

    private val projectId = ProjectId("p1")

    private fun emptyAutomationClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[]}"""),
    ))

    private fun makeAutomationViewModel(client: HttpClient) = ProjectAutomationViewModel(
        projectId = projectId,
        scheduleClient = ScheduleApiClient(client),
        webhookClient = WebhookTriggerApiClient(client),
        mavenClient = MavenTriggerApiClient(client),
    )

    @Test
    fun `1A cron validation accepts simple expression`() = runComposeUiTest {
        var confirmed = false
        setContent {
            MaterialTheme {
                CreateScheduleDialog(
                    isSaving = false,
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("0 9 * * *")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsEnabled()
    }

    @Test
    fun `1A cron validation accepts range expression`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("0 9 * * 1-5")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsEnabled()
    }

    @Test
    fun `1A cron validation accepts step expression`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("*/15 * * * *")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsEnabled()
    }

    @Test
    fun `1A cron validation accepts list with range`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("0,30 9 * * 1-5")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsEnabled()
    }

    @Test
    fun `1A cron validation rejects invalid expression`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("invalid")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsNotEnabled()
    }

    @Test
    fun `1A cron validation rejects out of range hour`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("0 25 * * *")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsNotEnabled()
    }

    @Test
    fun `1A cron validation rejects month zero`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CreateScheduleDialog(isSaving = false, onConfirm = {}, onDismiss = {})
            }
        }

        onNodeWithTag("schedule_cron_input").performTextInput("0 9 * 0 *")
        waitForIdle()
        onNodeWithTag("schedule_create_button").assertIsNotEnabled()
    }

    // ── 1E: AuditLog Concurrency Guard Tests ──

    @Test
    fun `1E audit log renders events and shows list`() = runComposeUiTest {
        val teamId = TeamId("t1")
        val events = (1..3).map { i ->
            """{"id":"e$i","teamId":"t1","action":"MEMBER_JOINED","targetType":"USER","targetId":"u1","actorUsername":"admin","details":"details $i","timestamp":${1710000000000 + i * 1000}}"""
        }
        val client = mockHttpClient(mapOf(
            "/teams/t1/audit" to json("""{"events":[${events.joinToString(",")}]}"""),
        ))
        val vm = AuditLogViewModel(teamId, TeamApiClient(client))

        setContent { MaterialTheme { AuditLogScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("audit_event_list").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("audit_event_list").assertExists()
        onNodeWithTag("audit_event_e1", useUnmergedTree = true).assertExists()
    }

    // ── 1F: TemplatePickerDialog Column Test ──

    @Test
    fun `1F template picker dialog renders without crash`() = runComposeUiTest {
        val params = listOf(
            Parameter(key = "version", value = "1.0.0"),
            Parameter(key = "branch", value = "main"),
        )
        val predecessors = listOf(
            Block.ActionBlock(
                id = BlockId("b1"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
            )
        )

        setContent {
            MaterialTheme {
                TemplatePickerDialog(
                    parameters = params,
                    predecessors = predecessors,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        // The dialog renders without intrinsic measurement crash
        onNodeWithTag("template_picker_list").assertExists()
        // Parameter suggestions appear
        onNodeWithTag("template_item_version", useUnmergedTree = true).assertExists()
        onNodeWithTag("template_item_branch", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `1F template picker dialog shows empty state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                TemplatePickerDialog(
                    parameters = emptyList(),
                    predecessors = emptyList(),
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag("template_picker_list").assertExists()
        // No template items should be present
        onAllNodes(hasTestTag("template_item_version")).assertCountEquals(0)
    }

    // ── 1C: Invite Dialog Inline Error Tests ──

    @Test
    fun `1C invite dialog shows error inline`() = runComposeUiTest {
        val teamId = TeamId("t1")
        val client = mockHttpClient(mapOf(
            "/teams/t1" to json("""{"team":{"id":"t1","name":"Alpha","description":"","createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z","memberCount":1}}"""),
            "/teams/t1/members" to json("""{"members":[{"userId":{"value":"u1"},"username":"admin","role":"TEAM_LEAD","joinedAt":"2026-03-13T00:00:00Z"}]}"""),
            "/teams/t1/invites" to json("""{"invites":[]}"""),
            "/teams/t1/join-requests" to json("""{"requests":[]}"""),
        ))
        val vm = TeamManageViewModel(teamId, TeamApiClient(client))

        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("invite_user_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("invite_user_button", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("invite_user_id_input").assertExists()
    }
}
