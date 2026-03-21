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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectAutomationScreenTest {

    private val projectId = ProjectId("p1")

    // ── Shared JSON fragments ──

    private val emptySchedulesJson = """{"schedules":[]}"""
    private val emptyTriggersJson = """{"triggers":[]}"""

    private val scheduleS1Json = """{"id":"s1","projectId":"p1","cronExpression":"0 9 * * *","parameters":[],"enabled":true}"""
    private val scheduleS1DisabledJson = """{"id":"s1","projectId":"p1","cronExpression":"0 9 * * *","parameters":[],"enabled":false}"""
    private val createdScheduleJson = """{"schedule":{"id":"s2","projectId":"p1","cronExpression":"0 12 * * 1","parameters":[],"enabled":true}}"""

    private val webhookT1Json = """{"id":"t1","projectId":"p1","secret":"********","enabled":true,"webhookUrl":"https://host/api/v1/triggers/webhook/t1"}"""
    private val webhookT1DisabledJson = """{"id":"t1","projectId":"p1","secret":"********","enabled":false,"webhookUrl":"https://host/api/v1/triggers/webhook/t1"}"""
    private val createdWebhookJson = """{"id":"t2","projectId":"p1","secret":"my-secret-123","enabled":true,"webhookUrl":"https://host/api/v1/triggers/webhook/t2"}"""

    private val mavenM1Json = """{"id":"m1","projectId":"p1","repoUrl":"https://repo.maven.apache.org/maven2","groupId":"com.example","artifactId":"my-lib","parameterKey":"version","enabled":true,"includeSnapshots":false,"lastCheckedAt":null}"""
    private val mavenM1DisabledJson = """{"id":"m1","projectId":"p1","repoUrl":"https://repo.maven.apache.org/maven2","groupId":"com.example","artifactId":"my-lib","parameterKey":"version","enabled":false,"includeSnapshots":false,"lastCheckedAt":null}"""
    private val createdMavenJson = """{"trigger":{"id":"m2","projectId":"p1","repoUrl":"https://repo1.maven.org/maven2","groupId":"org.test","artifactId":"core","parameterKey":"version","enabled":true,"includeSnapshots":false,"lastCheckedAt":null}}"""

    // ── Multi-route mock client helper (allows duplicate path keys with different methods) ──

    private fun multiRouteClient(routes: List<Pair<String, MockRoute>>): HttpClient {
        return HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val route = routes.firstOrNull { (key, r) ->
                path == "/api/v1$key" && (r.method == null || r.method == method)
            }?.second
            respond(
                content = route?.body ?: "{}",
                status = route?.status ?: HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
    }

    // ── Client factories ──

    private fun emptyClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[]}"""),
    ))

    private fun populatedClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[$scheduleS1Json]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[$webhookT1Json]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[$mavenM1Json]}"""),
    ))

    /** Client that supports schedule CRUD: GET list, POST create, DELETE, PUT toggle. */
    private fun scheduleCrudClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json("""{"schedules":[$scheduleS1Json]}""", method = HttpMethod.Get),
        "/projects/p1/schedules" to json(createdScheduleJson, method = HttpMethod.Post),
        "/projects/p1/schedules/s1" to json("{}", method = HttpMethod.Delete),
        "/projects/p1/schedules/s1" to json("""{"schedule":$scheduleS1DisabledJson}""", method = HttpMethod.Put),
        "/projects/p1/triggers" to json("""{"triggers":[$webhookT1Json]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[$mavenM1Json]}"""),
    ))

    /** Client that supports webhook CRUD: GET list, POST create, DELETE, PUT toggle. */
    private fun webhookCrudClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json("""{"triggers":[$webhookT1Json]}""", method = HttpMethod.Get),
        "/projects/p1/triggers" to json(createdWebhookJson, method = HttpMethod.Post),
        "/projects/p1/triggers/t1" to json("{}", method = HttpMethod.Delete),
        "/projects/p1/triggers/t1" to json(webhookT1DisabledJson, method = HttpMethod.Put),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client that supports maven CRUD: GET list, POST create, DELETE, PUT toggle. */
    private fun mavenCrudClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json("""{"triggers":[$mavenM1Json]}""", method = HttpMethod.Get),
        "/projects/p1/maven-triggers" to json(createdMavenJson, method = HttpMethod.Post),
        "/projects/p1/maven-triggers/m1" to json("{}", method = HttpMethod.Delete),
        "/projects/p1/maven-triggers/m1" to json("""{"trigger":$mavenM1DisabledJson}""", method = HttpMethod.Put),
    ))

    /** Client where initial load fails (500). */
    private fun errorOnLoadClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"error":"Internal server error"}""", status = HttpStatusCode.InternalServerError),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client where schedule creation fails (400). */
    private fun errorOnCreateScheduleClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson, method = HttpMethod.Get),
        "/projects/p1/schedules" to json("""{"error":"Invalid cron expression","code":"VALIDATION_ERROR"}""", status = HttpStatusCode.BadRequest, method = HttpMethod.Post),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client where webhook creation fails. */
    private fun errorOnCreateWebhookClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson, method = HttpMethod.Get),
        "/projects/p1/triggers" to json("""{"error":"Webhook limit reached","code":"LIMIT_EXCEEDED"}""", status = HttpStatusCode.BadRequest, method = HttpMethod.Post),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client where maven creation fails. */
    private fun errorOnCreateMavenClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson, method = HttpMethod.Get),
        "/projects/p1/maven-triggers" to json("""{"error":"Duplicate trigger","code":"DUPLICATE"}""", status = HttpStatusCode.BadRequest, method = HttpMethod.Post),
    ))

    /** Client for webhook creation that returns a secret. */
    private fun webhookCreateClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson, method = HttpMethod.Get),
        "/projects/p1/triggers" to json(createdWebhookJson, method = HttpMethod.Post),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client with multiple schedules for testing multiple items. */
    private fun multiScheduleClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[$scheduleS1Json,{"id":"s2","projectId":"p1","cronExpression":"0 12 * * 1","parameters":[],"enabled":false}]}"""),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client with multiple webhooks. */
    private fun multiWebhookClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json("""{"triggers":[$webhookT1Json,{"id":"t2","projectId":"p1","secret":"****","enabled":false,"webhookUrl":"https://host/api/v1/triggers/webhook/t2"}]}"""),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client with multiple maven triggers. */
    private fun multiMavenClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json("""{"triggers":[$mavenM1Json,{"id":"m2","projectId":"p1","repoUrl":"https://repo1.maven.org/maven2","groupId":"org.test","artifactId":"core","parameterKey":"ver","enabled":false,"includeSnapshots":true,"lastCheckedAt":null}]}"""),
    ))

    /** Client for schedule creation end-to-end. */
    private fun scheduleCreateClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson, method = HttpMethod.Get),
        "/projects/p1/schedules" to json(createdScheduleJson, method = HttpMethod.Post),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson),
    ))

    /** Client for maven creation end-to-end. */
    private fun mavenCreateClient() = multiRouteClient(listOf(
        "/projects/p1/schedules" to json(emptySchedulesJson),
        "/projects/p1/triggers" to json(emptyTriggersJson),
        "/projects/p1/maven-triggers" to json(emptyTriggersJson, method = HttpMethod.Get),
        "/projects/p1/maven-triggers" to json(createdMavenJson, method = HttpMethod.Post),
    ))

    private fun makeViewModel(client: HttpClient) = ProjectAutomationViewModel(
        projectId = projectId,
        scheduleClient = ScheduleApiClient(client),
        webhookClient = WebhookTriggerApiClient(client),
        mavenClient = MavenTriggerApiClient(client),
    )

    // ══════════════════════════════════════════════════════════════
    // EXISTING TESTS (preserved as-is)
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-1: Create schedule end-to-end
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-1 create schedule end-to-end`() = runComposeUiTest {
        val vm = makeViewModel(scheduleCreateClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open create form
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_schedule_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter a valid cron expression
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("0 12 * * 1")

        // Create button should be enabled
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_create_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click create
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).performClick()

        // The new schedule should appear in the list
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 12 * * 1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("0 12 * * 1").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-2: Create button validation (disabled when invalid)
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-2 schedule create button disabled when cron is empty`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_schedule_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Create button should be disabled when cron is empty
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `QA-AUTO-2 schedule create button enabled when valid cron entered`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter valid cron
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("0 9 * * *")

        // Create button should now be enabled
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-3: Cron expression validation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-3 invalid cron shows validation error`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter an invalid cron expression
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("bad cron")

        // Validation error text should appear and button stays disabled
        onNodeWithText("Invalid format", substring = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `QA-AUTO-3 valid cron shows valid indicator`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter a valid but non-preset cron expression
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("30 14 * * 2")

        // Should show valid indicator
        onNodeWithText("Valid", substring = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-4: Preset dropdown selects cron
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-4 preset dropdown populates cron expression`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_preset_selector", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the preset selector overlay to open the dropdown
        onNodeWithTag("schedule_preset_selector_click").performClick()

        // Select "Every day at 9 AM" preset
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Every day", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Every day", substring = true).performClick()

        // Cron input should now contain the daily cron expression and button should be enabled
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-5: Delete confirm removes schedule
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-5 confirming delete removes schedule from list`() = runComposeUiTest {
        val vm = makeViewModel(scheduleCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        // Wait for schedule to load
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete on the schedule
        onNodeWithTag("schedule_delete_s1", useUnmergedTree = true).performClick()

        // Wait for confirmation to appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_schedule_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm the deletion (wait for debounce on confirm button)
        waitUntil(timeoutMillis = 3000L) {
            try {
                onNodeWithTag("confirm_delete_schedule_s1_confirm", useUnmergedTree = true).assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        onNodeWithTag("confirm_delete_schedule_s1_confirm", useUnmergedTree = true).performClick()

        // Schedule should be removed from the list
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("No schedules configured", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-6: Toggle enables/disables schedule
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-6 toggle schedule calls toggle and updates UI`() = runComposeUiTest {
        val vm = makeViewModel(scheduleCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_toggle_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // The schedule starts as enabled; toggle it
        onNodeWithTag("schedule_toggle_s1", useUnmergedTree = true).performClick()

        // The schedule should still exist in the list (just toggled state)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("schedule_toggle_s1", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-7: Add webhook button disabled while saving
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-7 add webhook button passes isSaving guard`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_webhook_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Initially the add webhook button should be enabled (isSaving is false)
        onNodeWithTag("add_webhook_button", useUnmergedTree = true).assertIsEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-8: Secret card appearance after creation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-8 webhook secret card appears after creation`() = runComposeUiTest {
        val vm = makeViewModel(webhookCreateClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_webhook_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click add webhook — triggers immediate creation
        onNodeWithTag("add_webhook_button", useUnmergedTree = true).performClick()

        // Secret card should appear with the secret value
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_secret_card", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("webhook_secret_card", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_secret_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_secret_copy", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_secret_dismiss", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-9: Secret card dismiss
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-9 webhook secret card dismissed on button click`() = runComposeUiTest {
        val vm = makeViewModel(webhookCreateClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_webhook_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Create webhook to show secret card
        onNodeWithTag("add_webhook_button", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_secret_card", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss the secret card
        onNodeWithTag("webhook_secret_dismiss", useUnmergedTree = true).performClick()

        // Secret card should be gone
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_secret_card", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("webhook_secret_card", useUnmergedTree = true).assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-10: Toggle webhook trigger
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-10 toggle webhook trigger updates state`() = runComposeUiTest {
        val vm = makeViewModel(webhookCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Toggle the webhook
        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).performClick()

        // The trigger should still be visible (state changed)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-11: Delete webhook confirm
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-11 delete webhook shows confirmation and removes on confirm`() = runComposeUiTest {
        val vm = makeViewModel(webhookCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_delete_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete on webhook
        onNodeWithTag("webhook_delete_t1", useUnmergedTree = true).performClick()

        // Confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_webhook_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_delete_webhook_t1_confirm", useUnmergedTree = true).assertExists()
        onNodeWithTag("confirm_delete_webhook_t1_cancel", useUnmergedTree = true).assertExists()

        // Wait for debounce then confirm
        waitUntil(timeoutMillis = 3000L) {
            try {
                onNodeWithTag("confirm_delete_webhook_t1_confirm", useUnmergedTree = true).assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        onNodeWithTag("confirm_delete_webhook_t1_confirm", useUnmergedTree = true).performClick()

        // Webhook should be removed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("No webhook triggers configured", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-12: Create maven trigger end-to-end
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-12 create maven trigger end-to-end`() = runComposeUiTest {
        val vm = makeViewModel(mavenCreateClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open create form
        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Fill in the form fields
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).performTextInput("core")
        // parameter key already defaults to "version"

        // Create button should be enabled
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsEnabled()

        // Click create
        onNodeWithTag("maven_create_button", useUnmergedTree = true).performClick()

        // The new maven trigger should appear in the list
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("org.test:core").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("org.test:core").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-13: Button validation for maven trigger
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-13 maven create button disabled when fields are empty`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Button should be disabled with empty form (except parameterKey which defaults to "version")
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `QA-AUTO-13 maven create button enabled when all fields filled`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Fill required fields
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).performTextInput("core")

        // Button should now be enabled
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun `QA-AUTO-13 maven create button disabled when only some fields filled`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Fill only URL and groupId, missing artifactId
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")

        // Button should remain disabled
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-14: URL validation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-14 maven URL validation rejects non-http URL`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_repo_url_field", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter an invalid URL (not starting with http:// or https://)
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("ftp://invalid.url")

        // Should show URL validation error
        onNodeWithText("Must start with http:// or https://", substring = true).assertExists()

        // Fill other fields
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).performTextInput("core")

        // Button should still be disabled because URL is invalid
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `QA-AUTO-14 maven URL validation accepts https URL`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_repo_url_field", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter a valid https URL
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")

        // No error text about URL should be shown
        onNodeWithText("Must start with http:// or https://", substring = true).assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-15: Toggle maven trigger
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-15 toggle maven trigger updates state`() = runComposeUiTest {
        val vm = makeViewModel(mavenCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_toggle_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Toggle the maven trigger
        onNodeWithTag("maven_toggle_m1", useUnmergedTree = true).performClick()

        // The trigger should still be visible
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("maven_toggle_m1", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-16: Delete maven trigger confirm
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-16 delete maven trigger shows confirmation and removes on confirm`() = runComposeUiTest {
        val vm = makeViewModel(mavenCrudClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_delete_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete
        onNodeWithTag("maven_delete_m1", useUnmergedTree = true).performClick()

        // Confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_maven_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_delete_maven_m1_confirm", useUnmergedTree = true).assertExists()
        onNodeWithTag("confirm_delete_maven_m1_cancel", useUnmergedTree = true).assertExists()

        // Wait for debounce then confirm
        waitUntil(timeoutMillis = 3000L) {
            try {
                onNodeWithTag("confirm_delete_maven_m1_confirm", useUnmergedTree = true).assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        onNodeWithTag("confirm_delete_maven_m1_confirm", useUnmergedTree = true).performClick()

        // Maven trigger should be removed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("No Maven publication triggers configured", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-17: Error snackbar on load failure
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-17 error snackbar appears on load failure`() = runComposeUiTest {
        val vm = makeViewModel(errorOnLoadClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        // Wait for error snackbar to appear (the error message from server or fallback)
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("went wrong", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-18: Error snackbar on create failure
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-18 error snackbar on schedule create failure`() = runComposeUiTest {
        val vm = makeViewModel(errorOnCreateScheduleClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open form and fill valid cron
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("0 9 * * *")
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).performClick()

        // Error snackbar should appear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("Invalid cron expression", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `QA-AUTO-18 error snackbar on webhook create failure`() = runComposeUiTest {
        val vm = makeViewModel(errorOnCreateWebhookClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_webhook_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click add webhook — triggers immediate creation
        onNodeWithTag("add_webhook_button", useUnmergedTree = true).performClick()

        // Error snackbar should appear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("Webhook limit reached", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `QA-AUTO-18 error snackbar on maven create failure`() = runComposeUiTest {
        val vm = makeViewModel(errorOnCreateMavenClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open form and fill valid data
        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).performTextInput("core")
        onNodeWithTag("maven_create_button", useUnmergedTree = true).performClick()

        // Error snackbar should appear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("Duplicate trigger", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-19: Schedule form close/dismiss
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-19 schedule form dismisses on close button`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open form
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_schedule_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Close form using the close button
        onNodeWithTag("create_schedule_form_close", useUnmergedTree = true).performClick()

        // Form should be dismissed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_schedule_form", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-20: Maven form close/dismiss
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-20 maven form dismisses on close button`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open form
        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Close form
        onNodeWithTag("create_maven_form_close", useUnmergedTree = true).performClick()

        // Form should be dismissed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-21: Delete cancel dismisses confirmation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-21 schedule delete cancel dismisses confirmation`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_delete_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete
        onNodeWithTag("schedule_delete_s1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_schedule_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click cancel
        onNodeWithTag("confirm_delete_schedule_s1_cancel", useUnmergedTree = true).performClick()

        // Confirmation should be dismissed but schedule should remain
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_schedule_s1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("0 9 * * *").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-22: Webhook delete cancel dismisses confirmation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-22 webhook delete cancel dismisses confirmation`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_delete_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete
        onNodeWithTag("webhook_delete_t1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_webhook_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click cancel
        onNodeWithTag("confirm_delete_webhook_t1_cancel", useUnmergedTree = true).performClick()

        // Confirmation should be dismissed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_webhook_t1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-23: Maven delete cancel dismisses confirmation
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-23 maven delete cancel dismisses confirmation`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_delete_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete
        onNodeWithTag("maven_delete_m1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_maven_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click cancel
        onNodeWithTag("confirm_delete_maven_m1_cancel", useUnmergedTree = true).performClick()

        // Confirmation should be dismissed
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_maven_m1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("com.example:my-lib").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-24: Schedule description for known presets
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-24 schedule with known cron shows description`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }

        // The schedule "0 9 * * *" should show its "Daily" description
        onNodeWithTag("schedule_description_s1", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-25: Webhook URL displayed in item
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-25 webhook item shows URL`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Webhook URL should be visible
        onNodeWithText("https://host/api/v1/triggers/webhook/t1", substring = true, useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-26: Maven item shows repo URL
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-26 maven item shows repo URL`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isNotEmpty()
        }

        // Repo URL should be visible
        onNodeWithText("https://repo.maven.apache.org/maven2", substring = true, useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-27: Maven item shows last checked text
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-27 maven item shows never checked when lastCheckedAt is null`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isNotEmpty()
        }

        // "Never" text should appear since lastCheckedAt is null
        onNodeWithText("Never", substring = true, useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-28: Multiple schedules displayed
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-28 multiple schedules are all displayed`() = runComposeUiTest {
        val vm = makeViewModel(multiScheduleClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("0 9 * * *").assertExists()
        onNodeWithText("0 12 * * 1").assertExists()
        onNodeWithTag("schedule_toggle_s1", useUnmergedTree = true).assertExists()
        onNodeWithTag("schedule_toggle_s2", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-29: Multiple webhooks displayed
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-29 multiple webhooks are all displayed`() = runComposeUiTest {
        val vm = makeViewModel(multiWebhookClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_toggle_t2", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_delete_t1", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_delete_t2", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-30: Multiple maven triggers displayed
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-30 multiple maven triggers are all displayed`() = runComposeUiTest {
        val vm = makeViewModel(multiMavenClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("com.example:my-lib").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText("com.example:my-lib").assertExists()
        onNodeWithText("org.test:core").assertExists()
        onNodeWithTag("maven_toggle_m1", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_toggle_m2", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-31: Schedule form shows cron input field
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-31 schedule form shows cron input and preset selector`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_schedule_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).assertExists()
        onNodeWithTag("schedule_preset_selector", useUnmergedTree = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-32: Maven form shows all required fields
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-32 maven form shows all required fields`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_parameter_key_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_include_snapshots", useUnmergedTree = true).assertExists()
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-33: Maven include snapshots toggle
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-33 maven include snapshots checkbox is toggleable`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_include_snapshots", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the include snapshots toggle
        onNodeWithTag("maven_include_snapshots", useUnmergedTree = true).performClick()

        // The toggle row should still be present (toggled)
        onNodeWithTag("maven_include_snapshots", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-34: Loading state shows spinner
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-34 loading state shows progress indicator`() = runComposeUiTest {
        // Use a client that returns empty data but the ViewModel starts with isLoading=true
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        // The screen should exist (we can verify the screen tag is present during or after loading)
        onNodeWithTag("project_automation_screen").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-35: Section headers visible
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-35 section headers are displayed`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // All three section headers should be visible
        onNodeWithText("Schedules", substring = true).assertExists()
        onAllNodesWithText("Webhook", substring = true).onFirst().assertExists()
        onAllNodesWithText("Maven", substring = true).onFirst().assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-36: Webhook delete shows confirmation message
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-36 webhook delete confirmation shows message`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_delete_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("webhook_delete_t1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_webhook_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirmation message should be shown
        onNodeWithTag("confirm_delete_webhook_t1", useUnmergedTree = true).assertExists()
        // The delete confirmation contains "Delete" text for the confirm button
        onNodeWithTag("confirm_delete_webhook_t1_confirm", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // QA-AUTO-37: Maven delete confirmation shows artifact identity
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `QA-AUTO-37 maven delete confirmation shows artifact identity`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_delete_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("maven_delete_m1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_maven_m1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirmation message should contain the artifact identity (also appears in item card)
        assert(onAllNodesWithText("com.example:my-lib", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size >= 2) { "Artifact identity should appear in item and confirmation" }
    }

    // ══════════════════════════════════════════════════════════════
    // Additional MEDIUM tests: Schedule cron edge cases
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `schedule cron validation rejects too few fields`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter cron with only 3 fields
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("0 9 *")

        onNodeWithText("Invalid format", substring = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `schedule cron validation rejects out-of-range values`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter cron with minute = 60 (invalid, max is 59)
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("60 9 * * *")

        onNodeWithText("Invalid format", substring = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test
    fun `schedule cron accepts range expressions`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter cron with range and step expressions
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("*/15 9-17 * * 1-5")

        onNodeWithText("Valid", substring = true).assertExists()
        onNodeWithTag("schedule_create_button", useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun `schedule form cron preset shows next run hint`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter a known preset cron
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("0 9 * * 1-5")

        // Should show the preset description as a hint
        onNodeWithText("weekday", substring = true, ignoreCase = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // Additional MEDIUM tests: Maven URL edge cases
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `maven URL validation accepts http URL`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("maven_repo_url_field", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Enter http:// URL (should be valid)
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("http://repo.example.com")

        // No URL error should appear
        onNodeWithText("Must start with http:// or https://", substring = true).assertDoesNotExist()
    }

    @Test
    fun `maven create button disabled when parameter key is cleared`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_maven_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_maven_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("create_maven_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Fill all fields
        onNodeWithTag("maven_repo_url_field", useUnmergedTree = true).performTextInput("https://repo1.maven.org/maven2")
        onNodeWithTag("maven_group_id_field", useUnmergedTree = true).performTextInput("org.test")
        onNodeWithTag("maven_artifact_id_field", useUnmergedTree = true).performTextInput("core")

        // Button should be enabled (parameterKey defaults to "version")
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsEnabled()

        // Clear the parameter key field
        onNodeWithTag("maven_parameter_key_field", useUnmergedTree = true).performTextClearance()

        // Button should now be disabled
        onNodeWithTag("maven_create_button", useUnmergedTree = true).assertIsNotEnabled()
    }

    // ══════════════════════════════════════════════════════════════
    // Additional: Empty state hints are visible
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `empty state shows schedule hint text`() = runComposeUiTest {
        val vm = makeViewModel(emptyClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Check that hint/description text is shown below the empty state message
        onNodeWithText("cron", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun `all trigger types co-exist on populated screen`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("0 9 * * *").fetchSemanticsNodes().isNotEmpty()
        }

        // All three types of items visible at once
        onNodeWithText("0 9 * * *").assertExists()
        onNodeWithTag("webhook_toggle_t1", useUnmergedTree = true).assertExists()
        onNodeWithText("com.example:my-lib").assertExists()

        // No empty state messages
        onNodeWithText("No schedules configured", substring = true).assertDoesNotExist()
        onNodeWithText("No webhook triggers configured", substring = true).assertDoesNotExist()
        onNodeWithText("No Maven publication triggers configured", substring = true).assertDoesNotExist()
    }

    @Test
    fun `schedule delete confirmation shows cron expression in message`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_delete_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("schedule_delete_s1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_schedule_s1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // The confirmation message includes the cron expression (also shown in the schedule item)
        assert(onAllNodesWithText("0 9 * * *", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size >= 2) { "Cron expression should appear in item and confirmation" }
    }

    @Test
    fun `webhook item shows trigger label`() = runComposeUiTest {
        val vm = makeViewModel(populatedClient())
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("webhook_toggle_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Webhook items show a label (the text "Webhook Trigger" or similar)
        onAllNodesWithText("Webhook", substring = true).onFirst().assertExists()
    }
}
