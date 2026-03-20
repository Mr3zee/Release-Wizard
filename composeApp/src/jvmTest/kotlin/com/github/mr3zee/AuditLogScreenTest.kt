package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.teams.AuditLogScreen
import com.github.mr3zee.teams.AuditLogViewModel
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuditLogScreenTest {

    private val teamId = TeamId("t1")

    private val auditEventsJson = """{"events":[
        {"id":"evt1","teamId":"t1","actorUserId":"u1","actorUsername":"alice","action":"TEAM_CREATED","targetType":"TEAM","targetId":"t1","details":"Created team Alpha","timestamp":1700000000000},
        {"id":"evt2","teamId":"t1","actorUserId":"u2","actorUsername":"bob","action":"MEMBER_JOINED","targetType":"USER","targetId":"u2","details":"","timestamp":1700000060000}
    ],"pagination":{"totalCount":2,"offset":0,"limit":50}}"""

    private fun auditClient(json: String = auditEventsJson, status: HttpStatusCode = HttpStatusCode.OK) = mockHttpClient(
        mapOf("/teams/t1/audit" to json(json, status))
    )

    // QA-AUDIT-1: Screen renders with audit_log_screen testTag
    @Test
    fun `audit log screen renders with correct testTag`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("audit_log_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("audit_log_screen").assertExists()
    }

    // QA-AUDIT-2: Events list rendered with correct content
    @Test
    fun `audit log shows events with correct content`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("audit_event_evt1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Event cards exist
        onNodeWithTag("audit_event_evt1", useUnmergedTree = true).assertExists()
        onNodeWithTag("audit_event_evt2", useUnmergedTree = true).assertExists()
        // Action display names
        onNodeWithText("Team created", useUnmergedTree = true).assertExists()
        onNodeWithText("Member joined", useUnmergedTree = true).assertExists()
        // Actor usernames (in "by alice" / "by bob" text)
        onNodeWithText("alice", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("bob", substring = true, useUnmergedTree = true).assertExists()
        // Details text for first event
        onNodeWithText("Created team Alpha", useUnmergedTree = true).assertExists()
        // Event list tag
        onNodeWithTag("audit_event_list").assertExists()
    }

    // QA-AUDIT-3: Empty state shown when no events
    @Test
    fun `audit log shows empty state when no events`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient("""{"events":[],"pagination":{"totalCount":0,"offset":0,"limit":50}}""")))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No audit events yet.", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No audit events yet.").assertExists()
        onNodeWithText("Audit events will appear here as team activity occurs.").assertExists()
    }

    // QA-AUDIT-4: Initial load error shows snackbar with Retry
    @Test
    fun `audit log load error shows snackbar with retry`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient("{}", HttpStatusCode.InternalServerError)))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    // QA-AUDIT-5: Refresh button exists
    @Test
    fun `audit log has refresh button`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("audit_event_evt1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_button").assertExists()
    }

    // QA-AUDIT-6: Refresh error shows banner and keeps list
    @Test
    fun `audit log refresh error shows banner and keeps list`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams/t1/audit") -> {
                    if (!failOnRefresh.get()) {
                        respond(auditEventsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
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
        val vm = AuditLogViewModel(teamId, TeamApiClient(client))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Team created", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        // Original events should still be visible
        onNodeWithText("Team created", useUnmergedTree = true).assertExists()
    }

    // QA-AUDIT-7: Event details text displayed
    @Test
    fun `audit log event with details shows details text`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("audit_event_evt1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // evt1 has details "Created team Alpha"
        onNodeWithText("Created team Alpha", useUnmergedTree = true).assertExists()
        // evt2 has empty details, so no extra detail text should appear for it
    }

    // QA-AUDIT-8: Target type badges displayed
    @Test
    fun `audit log shows target type badges`() = runComposeUiTest {
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("audit_category_evt1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Target type badges with category testTags
        onNodeWithTag("audit_category_evt1", useUnmergedTree = true).assertExists()
        onNodeWithTag("audit_category_evt2", useUnmergedTree = true).assertExists()
        // Badge text: "Team" for evt1, "User" for evt2
        onNodeWithText("Team", useUnmergedTree = true).assertExists()
        onNodeWithText("User", useUnmergedTree = true).assertExists()
    }

    // QA-AUDIT-9: Pagination - load more when there are more items
    @Test
    fun `audit log loads more events when pagination indicates more pages`() = runComposeUiTest {
        val loadMoreRequested = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams/t1/audit") -> {
                    val offset = request.url.parameters["offset"]?.toIntOrNull() ?: 0
                    if (offset == 0) {
                        // First page: 1 event, totalCount=2 to trigger load more
                        respond(
                            """{"events":[
                                {"id":"evt1","teamId":"t1","actorUserId":"u1","actorUsername":"alice","action":"TEAM_CREATED","targetType":"TEAM","targetId":"t1","details":"","timestamp":1700000000000}
                            ],"pagination":{"totalCount":2,"offset":0,"limit":1}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    } else {
                        loadMoreRequested.set(true)
                        respond(
                            """{"events":[
                                {"id":"evt2","teamId":"t1","actorUserId":"u2","actorUsername":"bob","action":"MEMBER_JOINED","targetType":"USER","targetId":"u2","details":"","timestamp":1700000060000}
                            ],"pagination":{"totalCount":2,"offset":1,"limit":1}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders,
                        )
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = AuditLogViewModel(teamId, TeamApiClient(client))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        // Wait for first page
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Team created", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // loadMoreItem triggers automatically via LaunchedEffect when pagination has more
        waitUntil(timeoutMillis = 5000L) { loadMoreRequested.get() }
        // Second page event should also appear
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Member joined", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Member joined", useUnmergedTree = true).assertExists()
    }

    // Back button triggers onBack callback
    @Test
    fun `audit log back button triggers callback`() = runComposeUiTest {
        val backClicked = AtomicBoolean(false)
        val vm = AuditLogViewModel(teamId, TeamApiClient(auditClient()))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = { backClicked.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("back_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("back_button").performClick()
        assertTrue(backClicked.get(), "onBack callback should have been called")
    }

    // Refresh with new data shows updated events
    @Test
    fun `audit log refresh shows updated events`() = runComposeUiTest {
        val returnNewData = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams/t1/audit") -> {
                    val body = if (!returnNewData.get()) {
                        auditEventsJson
                    } else {
                        """{"events":[
                            {"id":"evt1","teamId":"t1","actorUserId":"u1","actorUsername":"alice","action":"TEAM_CREATED","targetType":"TEAM","targetId":"t1","details":"Created team Alpha","timestamp":1700000000000},
                            {"id":"evt2","teamId":"t1","actorUserId":"u2","actorUsername":"bob","action":"MEMBER_JOINED","targetType":"USER","targetId":"u2","details":"","timestamp":1700000060000},
                            {"id":"evt3","teamId":"t1","actorUserId":"u1","actorUsername":"alice","action":"PROJECT_CREATED","targetType":"PROJECT","targetId":"p1","details":"New project","timestamp":1700000120000}
                        ],"pagination":{"totalCount":3,"offset":0,"limit":50}}"""
                    }
                    respond(body, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = AuditLogViewModel(teamId, TeamApiClient(client))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Team created", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("audit_event_evt3", useUnmergedTree = true).assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("audit_event_evt3", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("audit_event_evt3", useUnmergedTree = true).assertExists()
    }

    // Dismiss refresh error hides banner
    @Test
    fun `audit log dismiss refresh error hides banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams/t1/audit") -> {
                    if (!failOnRefresh.get()) {
                        respond(auditEventsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
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
        val vm = AuditLogViewModel(teamId, TeamApiClient(client))
        setContent {
            MaterialTheme {
                AuditLogScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Team created", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }
}
