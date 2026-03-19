package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.automation.ProjectAutomationScreen
import com.github.mr3zee.automation.ProjectAutomationViewModel
import com.github.mr3zee.model.ProjectId
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectAutomationScreenTest {

    private val projectId = ProjectId("p1")

    private fun emptyClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[]}"""),
    ))

    private fun populatedClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[{"id":"s1","projectId":"p1","cronExpression":"0 9 * * *","parameters":[],"enabled":true}]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[{"id":"t1","projectId":"p1","secret":"********","enabled":true,"webhookUrl":"https://host/api/v1/triggers/webhook/t1"}]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[{"id":"m1","projectId":"p1","repoUrl":"https://repo.maven.apache.org/maven2","groupId":"com.example","artifactId":"my-lib","parameterKey":"version","enabled":true,"includeSnapshots":false,"lastCheckedAt":null}]}"""),
    ))

    private fun makeViewModel(client: HttpClient) = ProjectAutomationViewModel(
        projectId = projectId,
        scheduleClient = ScheduleApiClient(client),
        webhookClient = WebhookTriggerApiClient(client),
        mavenClient = MavenTriggerApiClient(client),
    )

    @Test
    fun `automation screen renders with three add buttons`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        // Wait for data to load — buttons only appear after isLoading becomes false
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_automation_screen").assertExists()
        // Add buttons are inside RwButton (UnstyledButton) — requires useUnmergedTree
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).assertExists()
        onNodeWithTag("add_webhook_button", useUnmergedTree = true).assertExists()
        onNodeWithTag("add_maven_button", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `shows empty state messages when no triggers configured`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("No schedules configured", substring = true).assertExists()
        onNodeWithText("No webhook triggers configured", substring = true).assertExists()
        onNodeWithText("No Maven publication triggers configured", substring = true).assertExists()
    }

    @Test
    fun `shows schedule item when schedule exists`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("0 9 * * *").assertExists()
        onNodeWithTag("schedule_toggle_s1", useUnmergedTree = true).assertExists()
        onNodeWithTag("schedule_delete_s1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `shows webhook trigger item when trigger exists`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_delete_t1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `shows maven trigger item when trigger exists`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("com.example:my-lib").assertExists()
        onNodeWithTag("maven_toggle_m1", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_delete_m1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `clicking add schedule button opens create dialog`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        onNodeWithText("New Schedule", substring = true).assertExists()
    }

    @Test
    fun `clicking add maven trigger button opens create dialog`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        onNodeWithText("New Maven Publication Trigger", substring = true).assertExists()
    }

    @Test
    fun `clicking delete button shows confirmation dialog with confirm and cancel`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_delete_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("schedule_delete_s1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_schedule_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Confirm and cancel buttons appear in the inline confirmation (tagged with sub-tags)
        onNodeWithTag("confirm_delete_schedule_s1_confirm", useUnmergedTree = true).assertExists()
        onNodeWithTag("confirm_delete_schedule_s1_cancel", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `back button calls onBack`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        var backCalled = false
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = { backCalled = true }) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("automation_back_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("automation_back_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 1000L) { backCalled }
        assertTrue(backCalled)
    }
}
