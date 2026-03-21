package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.model.UserId
import com.github.mr3zee.persistence.OAuthAccountTable
import com.github.mr3zee.persistence.UserTable
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests OAuth-related auth behaviors using direct DB inserts to simulate OAuth-only users.
 */
class OAuthBehaviorTest {

    /**
     * Creates an OAuth-only user directly in DB (null passwordHash).
     * Returns username.
     */
    private fun createOAuthUserInDb(
        username: String = "oauth.user",
        role: UserRole = UserRole.USER,
    ): String {
        val db = getKoin().get<Database>()
        val userId = UUID.randomUUID()
        val now = Clock.System.now()
        transaction(db) {
            UserTable.insert {
                it[UserTable.id] = userId
                it[UserTable.username] = username
                it[UserTable.passwordHash] = null
                it[UserTable.role] = role
                it[UserTable.createdAt] = now
            }
            OAuthAccountTable.insert {
                it[OAuthAccountTable.id] = UUID.randomUUID()
                it[OAuthAccountTable.userId] = userId
                it[OAuthAccountTable.provider] = "google"
                it[OAuthAccountTable.providerUserId] = "google-${UUID.randomUUID()}"
                it[OAuthAccountTable.email] = "$username@gmail.com"
                it[OAuthAccountTable.displayName] = username
                it[OAuthAccountTable.createdAt] = now
            }
        }
        return username
    }

    private suspend fun io.ktor.client.HttpClient.register(
        username: String = "admin",
        password: String = "adminpass",
    ) = post(ApiRoutes.Auth.REGISTER) {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(username = username, password = password))
    }

    private suspend fun io.ktor.client.HttpClient.login(
        username: String = "admin",
        password: String = "adminpass",
    ) = post(ApiRoutes.Auth.LOGIN) {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest(username = username, password = password))
    }

    @Test
    fun `password login for OAuth-only user returns same 401 as invalid credentials`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // Create admin first (so OAuth user isn't first)
        client.register()

        // Create OAuth user directly in DB
        val oauthUsername = createOAuthUserInDb()

        // Attempt password login — returns 401 with same code as invalid credentials
        // to prevent account type enumeration (SEC-L1)
        val response = client.login(oauthUsername, "anything")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body: ErrorResponse = response.body()
        assertEquals("INVALID_CREDENTIALS", body.code)
    }

    @Test
    fun `me endpoint shows hasPassword true for password user`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()
        client.login()

        val meResponse = client.get(ApiRoutes.Auth.ME)
        val userInfo: UserInfo = meResponse.body()
        assertTrue(userInfo.hasPassword, "Password user should have hasPassword=true")
        assertTrue(userInfo.oauthProviders.isEmpty(), "Password user should have no OAuth providers")
    }

    @Test
    fun `OAuth-only user change username without password succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()
        createOAuthUserInDb()

        val authService = getKoin().get<AuthService>()
        val user = authService.getUserByUsername("oauth.user")
            ?: error("OAuth user not found")

        val result = authService.changeUsername(user.id, "new-name", null)
        assertTrue(result.isSuccess)
        assertEquals("new-name", result.getOrThrow().username)
    }

    @Test
    fun `OAuth-only user can set password`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()
        createOAuthUserInDb()

        val authService = getKoin().get<AuthService>()
        val oauthService = getKoin().get<OAuthService>()
        val user = authService.getUserByUsername("oauth.user")
            ?: error("OAuth user not found")

        assertFalse(oauthService.hasPassword(user.id))

        val result = authService.changePassword(user.id, null, "NewPass1")
        assertTrue(result.isSuccess)
        assertTrue(oauthService.hasPassword(user.id))

        // Can now login with password
        val loginResult = authService.validate("oauth.user", "NewPass1")
        assertTrue(loginResult != null)
    }

    @Test
    fun `password user cannot skip password when changing username`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()

        val authService = getKoin().get<AuthService>()
        val user = authService.getUserByUsername("admin")
            ?: error("User not found")

        val result = authService.changeUsername(user.id, "new-name", null)
        assertTrue(result.isFailure)
        assertEquals("INVALID_PASSWORD", result.exceptionOrNull()?.message)
    }

    @Test
    fun `OAuth-only user can delete account without password`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register() // admin first
        createOAuthUserInDb()

        val authService = getKoin().get<AuthService>()
        val user = authService.getUserByUsername("oauth.user")
            ?: error("OAuth user not found")

        val result = authService.deleteAccountSafe(user.id, "oauth.user", null)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `admin user list includes hasPassword and oauthProviders`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.register()
        client.login()
        createOAuthUserInDb()

        val response = client.get(ApiRoutes.Auth.USERS)
        assertEquals(HttpStatusCode.OK, response.status)
        val body: UserListResponse = response.body()
        assertEquals(2, body.users.size)

        val admin = body.users.first { it.username == "admin" }
        assertTrue(admin.hasPassword)
        assertTrue(admin.oauthProviders.isEmpty())

        val oauthUser = body.users.first { it.username == "oauth.user" }
        assertFalse(oauthUser.hasPassword)
        assertEquals(listOf(OAuthProvider.GOOGLE), oauthUser.oauthProviders)
    }

    @Test
    fun `OAuthService findOrCreateOAuthUser creates user and returns same on duplicate`() = testApplication {
        application { testModule() }
        // Trigger app initialization so Koin is active
        val client = jsonClient()
        client.get(ApiRoutes.HEALTH)

        val oauthService = getKoin().get<OAuthService>()
        val first = oauthService.findOrCreateOAuthUser("google", "g-1", "test@gmail.com", "Test User")
        val second = oauthService.findOrCreateOAuthUser("google", "g-1", "test@gmail.com", "Test User Updated")

        assertEquals(first.id, second.id, "Same provider + providerUserId should return same user")
    }

    @Test
    fun `OAuthService first user gets ADMIN role`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.get(ApiRoutes.HEALTH)

        val oauthService = getKoin().get<OAuthService>()
        val user = oauthService.findOrCreateOAuthUser("google", "g-1", "first@gmail.com", "First")
        assertEquals(UserRole.ADMIN, user.role)
    }

    @Test
    fun `OAuthService second user gets USER role`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.get(ApiRoutes.HEALTH)

        val oauthService = getKoin().get<OAuthService>()
        oauthService.findOrCreateOAuthUser("google", "g-1", "first@gmail.com", "First")
        val second = oauthService.findOrCreateOAuthUser("google", "g-2", "second@gmail.com", "Second")
        assertEquals(UserRole.USER, second.role)
    }

    @Test
    fun `username generated from display name`() {
        assertEquals("john.doe", DatabaseOAuthService.generateUsername("test@gmail.com", "John Doe"))
    }

    @Test
    fun `username falls back to email when no display name`() {
        assertEquals("john.smith", DatabaseOAuthService.generateUsername("john.smith@gmail.com", null))
    }

    @Test
    fun `username sanitizes special characters`() {
        assertEquals("john.doe.1", DatabaseOAuthService.generateUsername(null, "John (Doe) #1"))
    }
}
