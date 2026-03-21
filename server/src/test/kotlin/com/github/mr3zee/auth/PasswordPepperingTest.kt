package com.github.mr3zee.auth

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PasswordPepperingTest {

    // --- Core flow tests with pepper ---

    @Test
    fun `register and login work with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()

        val registerResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "pepperuser", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "pepperuser", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        val user = loginResponse.body<UserInfo>()
        assertEquals("pepperuser", user.username)
    }

    @Test
    fun `change password works with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()
        client.login(username = "pwuser", password = "oldpass123")

        val changeResponse = client.put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword = "oldpass123", newPassword = "newpass456"))
        }
        assertEquals(HttpStatusCode.OK, changeResponse.status)

        // Logout and login with new password
        client.post(ApiRoutes.Auth.LOGOUT)
        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "pwuser", password = "newpass456"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        // Old password should fail
        client.post(ApiRoutes.Auth.LOGOUT)
        val oldLoginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "pwuser", password = "oldpass123"))
        }
        assertEquals(HttpStatusCode.Unauthorized, oldLoginResponse.status)
    }

    @Test
    fun `password reset works with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()
        client.login(username = "resetuser", password = "original123")

        // Get user ID from /me
        val meResponse = client.get(ApiRoutes.Auth.ME)
        val userInfo = meResponse.body<UserInfo>()
        val userId = userInfo.id ?: error("Expected user ID")

        // Generate password reset token (admin operation — first user is admin)
        val generateResponse = client.post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            contentType(ContentType.Application.Json)
            setBody(GeneratePasswordResetRequest(userId = userId))
        }
        assertEquals(HttpStatusCode.OK, generateResponse.status)
        val resetLink = generateResponse.body<PasswordResetLinkResponse>()

        // Use the reset token to set a new password
        val resetResponse = client.post(ApiRoutes.Auth.RESET_PASSWORD) {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token = resetLink.token, newPassword = "resetpass789"))
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status)

        // Login with new password should work
        client.post(ApiRoutes.Auth.LOGOUT)
        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "resetuser", password = "resetpass789"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun `change username works with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()
        client.login(username = "oldname", password = "testpass123")

        val changeResponse = client.put(ApiRoutes.Auth.CHANGE_USERNAME) {
            contentType(ContentType.Application.Json)
            setBody(ChangeUsernameRequest(newUsername = "newname", currentPassword = "testpass123"))
        }
        assertEquals(HttpStatusCode.OK, changeResponse.status)
        val updated = changeResponse.body<UserInfo>()
        assertEquals("newname", updated.username)
    }

    @Test
    fun `delete account works with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()

        // Create a second user (first is admin and can't self-delete as last admin)
        client.login(username = "admin", password = "adminpass")
        val client2 = jsonClient()
        val registerResponse = client2.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "deleteme", password = "deletepass"))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)
        val loginResponse = client2.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "deleteme", password = "deletepass"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val deleteResponse = client2.delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody(DeleteAccountRequest(confirmUsername = "deleteme", currentPassword = "deletepass"))
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)
    }

    @Test
    fun `dummy hash path works with pepper — nonexistent user returns 401`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "nonexistent", password = "anypassword"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Pepper correctness tests ---

    @Test
    fun `cannot login with different pepper`() = testApplication {
        val sharedDb = testDbConfig()

        // Register with pepper A
        application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()
        val registerResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "peppertest", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login with pepper B (no old pepper fallback) should fail
        // Since we can't reconfigure the app mid-test, we use a second testApplication block
        // sharing the same H2 DB (DB_CLOSE_DELAY=-1 keeps it alive)
        testApplication {
            application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig(pepperSecret = TEST_PEPPER_ALT)) }
            val client2 = jsonClient()
            val loginResponse = client2.post(ApiRoutes.Auth.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "peppertest", password = "testpass123"))
            }
            assertEquals(HttpStatusCode.Unauthorized, loginResponse.status)
        }
    }

    @Test
    fun `pepper rotation — old pepper login succeeds and re-hashes`() = testApplication {
        val sharedDb = testDbConfig()

        // Register with pepper A
        application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()
        val registerResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "rotateuser", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        // Login with pepper B + old A — should succeed via rotation
        testApplication {
            application {
                testModule(
                    dbConfig = sharedDb,
                    authConfig = testAuthConfig(pepperSecret = TEST_PEPPER_ALT, pepperSecretOld = TEST_PEPPER),
                )
            }
            val client2 = jsonClient()
            val loginResponse = client2.post(ApiRoutes.Auth.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "rotateuser", password = "testpass123"))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status)
        }

        // After rotation, login with pepper B only (no old) should still work
        // because the hash was transparently upgraded during the previous login
        testApplication {
            application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig(pepperSecret = TEST_PEPPER_ALT)) }
            val client3 = jsonClient()
            val loginResponse = client3.post(ApiRoutes.Auth.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "rotateuser", password = "testpass123"))
            }
            assertEquals(HttpStatusCode.OK, loginResponse.status)
        }
    }

    // --- Edge cases ---

    @Test
    fun `unicode password works with pepper`() = testApplication {
        application { testModule(authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
        val client = jsonClient()

        val unicodePassword = "пароль123日本語"
        val registerResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "unicodeuser", password = unicodePassword))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "unicodeuser", password = unicodePassword))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }

    @Test
    fun `no pepper vs pepper produces different hashes`() = testApplication {
        val sharedDb = testDbConfig()

        // Register without pepper
        application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig()) }
        val client = jsonClient()
        client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "nopepperuser", password = "samepassword"))
        }

        // Login without pepper works
        val loginNoPepper = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "nopepperuser", password = "samepassword"))
        }
        assertEquals(HttpStatusCode.OK, loginNoPepper.status)

        // Try to login with pepper — should fail (hash is different)
        testApplication {
            application { testModule(dbConfig = sharedDb, authConfig = testAuthConfig(pepperSecret = TEST_PEPPER)) }
            val client2 = jsonClient()
            val loginWithPepper = client2.post(ApiRoutes.Auth.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = "nopepperuser", password = "samepassword"))
            }
            assertEquals(HttpStatusCode.Unauthorized, loginWithPepper.status)
        }
    }
}
