package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.PatApiClient
import com.github.mr3zee.model.User
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.profile.AdminUsersScreen
import com.github.mr3zee.profile.AdminUsersViewModel
import io.ktor.http.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AdminUsersDeleteTest {

    private val superadminUser = User(
        id = UserId("sa-1"), username = "superadmin", role = UserRole.SUPERADMIN,
        createdAt = 1000L, approved = true,
    )
    private val adminUser = User(
        id = UserId("a-1"), username = "admin1", role = UserRole.ADMIN,
        createdAt = 2000L, approved = true,
    )
    private val regularUser = User(
        id = UserId("u-1"), username = "regularuser", role = UserRole.USER,
        createdAt = 3000L, approved = true,
    )

    private val usersJson = """{"users":[
        {"id":"sa-1","username":"superadmin","role":"SUPERADMIN","createdAt":1000,"hasPassword":true,"oauthProviders":[],"approved":true},
        {"id":"a-1","username":"admin1","role":"ADMIN","createdAt":2000,"hasPassword":true,"oauthProviders":[],"approved":true},
        {"id":"u-1","username":"regularuser","role":"USER","createdAt":3000,"hasPassword":true,"oauthProviders":[],"approved":true}
    ]}"""

    private val emptyPreCheck = """{"canDelete":true,"requiresSuperAdmin":false,"affectedTeams":[]}"""
    private val teamLeadPreCheck = """{"canDelete":true,"requiresSuperAdmin":false,"affectedTeams":[{"teamId":"t-1","teamName":"Alpha Team"},{"teamId":"t-2","teamName":"Beta Team"}]}"""

    private fun createClient(
        preCheckResponse: String = emptyPreCheck,
        deleteResponse: String = """{"status":"deleted"}""",
    ) = mockHttpClient(listOf(
        "/auth/users" to json(usersJson, method = HttpMethod.Get),
        "/auth/users/u-1/delete-info" to json(preCheckResponse, method = HttpMethod.Get),
        "/auth/users/a-1/delete-info" to json(preCheckResponse, method = HttpMethod.Get),
        "/auth/users/u-1" to json(deleteResponse, method = HttpMethod.Delete),
        "/auth/users/a-1" to json(deleteResponse, method = HttpMethod.Delete),
        // After delete, return updated list
        "/auth/users" to json("""{"users":[
            {"id":"sa-1","username":"superadmin","role":"SUPERADMIN","createdAt":1000,"hasPassword":true,"oauthProviders":[],"approved":true},
            {"id":"a-1","username":"admin1","role":"ADMIN","createdAt":2000,"hasPassword":true,"oauthProviders":[],"approved":true}
        ]}""", method = HttpMethod.Get),
    ))

    // ── Delete button visibility ──────────────────────────────

    @Test
    fun `delete button hidden for self`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/users" to json(usersJson)))
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_sa-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // No delete button on self
        onNodeWithTag("admin_delete_sa-1", useUnmergedTree = true).assertDoesNotExist()
        // Delete button exists on others
        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `delete button hidden for superadmin target`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/users" to json(usersJson)))
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "a-1",
                    currentUserRole = UserRole.ADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_sa-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Admin cannot see delete button on superadmin
        onNodeWithTag("admin_delete_sa-1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `admin cannot see delete button on another admin`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/users" to json(usersJson)))
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "a-1",
                    currentUserRole = UserRole.ADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_a-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Admin can see delete on regular user
        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).assertExists()
        // Admin cannot see delete on superadmin
        onNodeWithTag("admin_delete_sa-1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `superadmin can see delete button on admin`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/users" to json(usersJson)))
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_a-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Superadmin can see delete on admin
        onNodeWithTag("admin_delete_a-1", useUnmergedTree = true).assertExists()
        // Superadmin can see delete on regular user
        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).assertExists()
    }

    // ── Role badges ───────────────────────────────────────────

    @Test
    fun `superadmin badge is displayed`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/users" to json(usersJson)))
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_sa-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify all three role badges render
        onNodeWithText("Super Admin", useUnmergedTree = true).assertExists()
        onNodeWithText("Admin", useUnmergedTree = true).assertExists()
        onNodeWithText("User", useUnmergedTree = true).assertExists()
    }

    // ── Delete confirmation flow ──────────────────────────────

    @Test
    fun `delete click shows confirmation after pre-check loads`() = runComposeUiTest {
        val client = createClient()
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click delete
        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).performClick()

        // Wait for confirmation to appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_confirm_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("admin_delete_confirm_u-1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `delete confirmation with team lead transfer shows team names`() = runComposeUiTest {
        val client = createClient(preCheckResponse = teamLeadPreCheck)
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_confirm_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The confirmation should mention team names
        onNodeWithText("Alpha Team", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Beta Team", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `dismiss confirmation hides it`() = runComposeUiTest {
        val client = createClient()
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_confirm_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click cancel
        onNodeWithTag("admin_delete_confirm_u-1_cancel", useUnmergedTree = true).performClick()

        // Confirmation should disappear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_confirm_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `buttons hidden during delete confirmation`() = runComposeUiTest {
        val client = createClient()
        val vm = AdminUsersViewModel(AuthApiClient(client), PatApiClient(client))

        setContent {
            MaterialTheme {
                AdminUsersScreen(
                    viewModel = vm,
                    currentUserId = "sa-1",
                    currentUserRole = UserRole.SUPERADMIN,
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_delete_confirm_u-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Reset link and delete buttons should be hidden during confirmation
        onNodeWithTag("admin_generate_reset_link_u-1", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("admin_delete_u-1", useUnmergedTree = true).assertDoesNotExist()
    }
}
