@file:Suppress("FunctionName")
@file:OptIn(ExperimentalTestApi::class)

package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.PatApiClient
import com.github.mr3zee.profile.AdminUsersScreen
import com.github.mr3zee.profile.AdminUsersViewModel
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.test.Test

class AdminUsersTokensTest {

    private val usersJson = """{"users":[
        {"id":"admin1","username":"admin","role":"ADMIN","createdAt":1700000000000,"approved":true,"hasPassword":true,"oauthProviders":[]},
        {"id":"user1","username":"alice","role":"USER","createdAt":1700000060000,"approved":true,"hasPassword":true,"oauthProviders":[]},
        {"id":"user2","username":"bob","role":"USER","createdAt":1700000120000,"approved":true,"hasPassword":true,"oauthProviders":[]}
    ]}"""

    private val aliceTokensJson = """[
        {"id":"pat1","name":"CI Token","expiresAt":null,"lastUsedAt":1700000000000,"createdAt":1700000000000,"revoked":false},
        {"id":"pat2","name":"Expired Token","expiresAt":1000000000000,"lastUsedAt":null,"createdAt":1000000000000,"revoked":false},
        {"id":"pat3","name":"Revoked Token","expiresAt":null,"lastUsedAt":null,"createdAt":1700000000000,"revoked":true}
    ]"""

    private val emptyTokensJson = "[]"

    private fun createViewModel(
        userTokensMap: Map<String, String> = mapOf("user1" to aliceTokensJson, "user2" to emptyTokensJson),
    ): AdminUsersViewModel {
        val authClient = AuthApiClient(mockHttpClient(mapOf(
            "/auth/users" to json(usersJson),
        )))
        val patClient = PatApiClient(mockHttpClient(mapOf(
            "/auth/users/user1/tokens" to json(userTokensMap["user1"] ?: "[]"),
            "/auth/users/user2/tokens" to json(userTokensMap["user2"] ?: "[]"),
        )))
        return AdminUsersViewModel(authClient, patClient)
    }

    @Test
    fun `active users show tokens button`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_user_item_user1").fetchSemanticsNodes().isNotEmpty()
        }
        // Self (admin1) should NOT have tokens button
        onNodeWithTag("admin_tokens_toggle_admin1").assertDoesNotExist()
        // Other users should have tokens button
        onNodeWithTag("admin_tokens_toggle_user1").assertExists()
        onNodeWithTag("admin_tokens_toggle_user2").assertExists()
    }

    @Test
    fun `clicking tokens button expands token list`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }
        // Token items should not be visible before expanding
        onNodeWithTag("admin_token_item_pat1").assertDoesNotExist()

        // Click tokens button
        onNodeWithTag("admin_tokens_toggle_user1").performClick()

        // Wait for tokens to load and display
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_token_item_pat1").assertExists()
        onNodeWithTag("admin_token_item_pat2").assertExists()
        onNodeWithTag("admin_token_item_pat3").assertExists()
    }

    @Test
    fun `token items show correct status badges`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_toggle_user1").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isNotEmpty()
        }

        // Active token should have revoke button
        onNodeWithTag("admin_revoke_token_pat1").assertExists()
        // Revoked token should NOT have revoke button
        onNodeWithTag("admin_revoke_token_pat3").assertDoesNotExist()
    }

    @Test
    fun `empty tokens shows empty state`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user2").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_toggle_user2").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_empty_user2").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_empty_user2").assertExists()
    }

    @Test
    fun `revoke shows confirmation then revokes`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_toggle_user1").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_revoke_token_pat1").fetchSemanticsNodes().isNotEmpty()
        }

        // Click revoke - should show confirmation
        onNodeWithTag("admin_revoke_token_pat1").performClick()
        waitForIdle()

        // Confirmation should appear
        onNodeWithTag("admin_revoke_confirm_pat1").assertExists()

        // Revoke button should be hidden during confirmation
        onNodeWithTag("admin_revoke_token_pat1").assertDoesNotExist()
    }

    @Test
    fun `collapsing one user collapses tokens`() = runComposeUiTest {
        val vm = createViewModel()
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }

        // Expand user1
        onNodeWithTag("admin_tokens_toggle_user1").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isNotEmpty()
        }

        // Click tokens button again to collapse
        onNodeWithTag("admin_tokens_toggle_user1").performClick()
        waitForIdle()

        // Tokens should be hidden (AnimatedVisibility may take a frame)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `expanding different user collapses previous`() = runComposeUiTest {
        val vm = createViewModel(
            mapOf("user1" to aliceTokensJson, "user2" to """[{"id":"pat4","name":"Bob Token","expiresAt":null,"lastUsedAt":null,"createdAt":1700000000000,"revoked":false}]"""),
        )
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }

        // Expand user1
        onNodeWithTag("admin_tokens_toggle_user1").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isNotEmpty()
        }

        // Expand user2 — should collapse user1
        onNodeWithTag("admin_tokens_toggle_user2").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat4").fetchSemanticsNodes().isNotEmpty()
        }

        // user1 tokens should be gone
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_token_item_pat1").fetchSemanticsNodes().isEmpty()
        }
    }

    // #13: Test network failure during token load shows error snackbar
    @Test
    fun `token load failure shows error snackbar`() = runComposeUiTest {
        val authClient = AuthApiClient(mockHttpClient(mapOf(
            "/auth/users" to json(usersJson),
        )))
        // Return 500 for token endpoints
        val patClient = PatApiClient(HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.contains("/tokens") -> respond("Server error", HttpStatusCode.InternalServerError, jsonHeaders)
                else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        })
        val vm = AdminUsersViewModel(authClient, patClient)
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_toggle_user1").performClick()

        // Should show a snackbar with server error message
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("Something went wrong", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // #14: Test revoke happy-path — confirm revoke, token reloads as revoked, snackbar shown
    @Test
    fun `revoke confirm completes and token shows as revoked`() = runComposeUiTest {
        val revoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val activeJson = """[{"id":"pat1","name":"CI Token","expiresAt":null,"lastUsedAt":null,"createdAt":1700000000000,"revoked":false}]"""
        val revokedJson = """[{"id":"pat1","name":"CI Token","expiresAt":null,"lastUsedAt":null,"createdAt":1700000000000,"revoked":true}]"""

        val authClient = AuthApiClient(mockHttpClient(mapOf(
            "/auth/users" to json(usersJson),
        )))
        val patClient = PatApiClient(HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path == "/api/v1/auth/users/user1/tokens/pat1" && method == HttpMethod.Delete -> {
                    revoked.set(true)
                    respond("", HttpStatusCode.NoContent, jsonHeaders)
                }
                path == "/api/v1/auth/users/user1/tokens" && method == HttpMethod.Get -> {
                    val body = if (revoked.get()) revokedJson else activeJson
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        })
        val vm = AdminUsersViewModel(authClient, patClient)
        setContent { MaterialTheme { AdminUsersScreen(viewModel = vm, currentUserId = "admin1", onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_tokens_toggle_user1").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("admin_tokens_toggle_user1").performClick()

        // Wait for active token with revoke button
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("admin_revoke_token_pat1").fetchSemanticsNodes().isNotEmpty()
        }

        // Click revoke → confirmation appears
        onNodeWithTag("admin_revoke_token_pat1").performClick()
        waitForIdle()
        onNodeWithTag("admin_revoke_confirm_pat1").assertExists()

        // Confirm revoke
        onNodeWithTag("admin_revoke_confirm_pat1_confirm").performClick()

        // After revoke, token reloads as revoked — revoke button should disappear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("admin_revoke_token_pat1").fetchSemanticsNodes().isEmpty()
        }

        // Snackbar should show "Token revoked"
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Token revoked", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
