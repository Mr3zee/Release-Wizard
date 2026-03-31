package com.github.mr3zee

import androidx.compose.ui.test.*
import com.github.mr3zee.api.UserNotificationApiClient
import com.github.mr3zee.model.UserNotification
import com.github.mr3zee.model.UserNotificationType
import com.github.mr3zee.notifications.NotificationsScreen
import com.github.mr3zee.notifications.NotificationsViewModel
import com.github.mr3zee.theme.AppTheme
import androidx.compose.runtime.LaunchedEffect
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NotificationsScreenTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val testNotifications = listOf(
        UserNotification(
            id = "n1", userId = "u1", type = UserNotificationType.TEAM_INVITE_RECEIVED,
            title = "Team invite", message = "You've been invited to Team A",
            read = false, timestamp = 1711929600000,
        ),
        UserNotification(
            id = "n2", userId = "u1", type = UserNotificationType.RELEASE_COMPLETED,
            title = "Release done", message = "Release v1.0 completed",
            read = false, timestamp = 1711929500000,
        ),
        UserNotification(
            id = "n3", userId = "u1", type = UserNotificationType.MEMBER_ROLE_CHANGED,
            title = "Role changed", message = "Your role was updated",
            read = true, timestamp = 1711929400000,
        ),
    )

    private fun notificationsToJson(list: List<UserNotification>): String {
        val notifJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(UserNotification.serializer()), list)
        return """{"notifications":$notifJson,"pagination":{"totalCount":${list.size},"offset":0,"limit":30}}"""
    }

    private fun notificationClient(
        notifications: AtomicReference<List<UserNotification>> = AtomicReference(testNotifications),
    ): HttpClient {
        return HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

            when {
                // List notifications
                path.endsWith("/user-notifications") && method == HttpMethod.Get -> {
                    respond(notificationsToJson(notifications.get()), status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                // Unread count
                path.endsWith("/unread-count") -> {
                    val count = notifications.get().count { !it.read }
                    respond("""{"count":$count}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                // Mark all read
                path.endsWith("/mark-all-read") && method == HttpMethod.Post -> {
                    notifications.set(notifications.get().map { it.copy(read = true) })
                    respond("""{"status":"read","count":0}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                // Mark single read: POST /user-notifications/{id}/read
                Regex(".*/user-notifications/([^/]+)/read$").find(path) != null && method == HttpMethod.Post -> {
                    val id = Regex(".*/user-notifications/([^/]+)/read$").find(path)?.groupValues?.get(1) ?: ""
                    notifications.set(notifications.get().map { if (it.id == id) it.copy(read = true) else it })
                    respond("""{"status":"read"}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                // Delete all read
                path.endsWith("/clear-read") && method == HttpMethod.Delete -> {
                    notifications.set(notifications.get().filter { !it.read })
                    respond("""{"status":"deleted","count":0}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                // Delete single: DELETE /user-notifications/{id}
                Regex(".*/user-notifications/([^/]+)$").find(path) != null && method == HttpMethod.Delete -> {
                    val id = Regex(".*/user-notifications/([^/]+)$").find(path)?.groupValues?.get(1) ?: ""
                    notifications.set(notifications.get().filter { it.id != id })
                    respond("""{"status":"deleted"}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(json) }
            install(HttpCookies)
            expectSuccess = true
        }
    }

    private fun createViewModel(
        client: HttpClient = notificationClient(),
        onUnreadCountChanged: (Long) -> Unit = {},
    ): NotificationsViewModel {
        val apiClient = UserNotificationApiClient(client)
        return NotificationsViewModel(apiClient, onUnreadCountChanged)
    }

    @Test
    fun `shows notification list with correct items`() = runComposeUiTest {
        val vm = createViewModel()
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Team invite").assertIsDisplayed()
        onNodeWithText("Release done").assertIsDisplayed()
        onNodeWithText("Role changed").assertIsDisplayed()
    }

    @Test
    fun `mark all read updates items visually`() = runComposeUiTest {
        val unreadCount = AtomicInteger(2)
        val vm = createViewModel(onUnreadCountChanged = { unreadCount.set(it.toInt()) })
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("mark_all_read_button").assertIsDisplayed()
        onNodeWithTag("mark_all_read_button").performClick()

        // After mark all read, the button should disappear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("mark_all_read_button").fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, unreadCount.get())
    }

    @Test
    fun `delete notification removes it from list`() = runComposeUiTest {
        val notifications = AtomicReference(testNotifications)
        val client = notificationClient(notifications)
        val vm = createViewModel(client)
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("notification_item_n1").assertIsDisplayed()

        // Find delete buttons by content description
        val deleteButtons = onAllNodesWithContentDescription("Delete notification", useUnmergedTree = true)
        assertTrue(deleteButtons.fetchSemanticsNodes().isNotEmpty(), "Delete buttons should be present")
        deleteButtons.onFirst().performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("notification_item_n1").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("Release done").assertIsDisplayed()
        onNodeWithText("Role changed").assertIsDisplayed()
    }

    @Test
    fun `delete all read removes only read notifications`() = runComposeUiTest {
        val notifications = AtomicReference(testNotifications)
        val client = notificationClient(notifications)
        val vm = createViewModel(client)
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("delete_all_read_button").assertIsDisplayed()
        onNodeWithTag("delete_all_read_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("notification_item_n3").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("Team invite").assertIsDisplayed()
        onNodeWithText("Release done").assertIsDisplayed()
    }

    @Test
    fun `mark all read then delete all read clears entire list`() = runComposeUiTest {
        val notifications = AtomicReference(testNotifications)
        val client = notificationClient(notifications)
        val vm = createViewModel(client)
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("mark_all_read_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("mark_all_read_button").fetchSemanticsNodes().isEmpty()
        }

        onNodeWithTag("delete_all_read_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No notifications yet.").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `empty state shown when no notifications`() = runComposeUiTest {
        val client = notificationClient(AtomicReference(emptyList()))
        val vm = createViewModel(client)
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No notifications yet.").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("No notifications yet.").assertIsDisplayed()
    }

    @Test
    fun `mark all read updates sidebar unread count optimistically`() = runComposeUiTest {
        val unreadCount = AtomicInteger(-1)
        val vm = createViewModel(onUnreadCountChanged = { unreadCount.set(it.toInt()) })
        setContent {
            LaunchedEffect(Unit) { vm.loadIfNeeded() }
            AppTheme {
                NotificationsScreen(viewModel = vm, onNavigate = {}, onBack = {})
            }
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team invite").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("mark_all_read_button").performClick()

        waitUntil(timeoutMillis = 1000L) { unreadCount.get() == 0 }
        assertEquals(0, unreadCount.get())
    }
}
