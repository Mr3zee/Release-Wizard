package com.github.mr3zee.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AuthDtosTest {

    @Test
    fun `LoginRequest toString redacts password`() {
        val request = LoginRequest(username = "admin", password = "secret123")
        val str = request.toString()
        assertContains(str, "admin")
        assertContains(str, "****")
        assertFalse(str.contains("secret123"))
    }

    @Test
    fun `RegisterRequest toString redacts password`() {
        val request = RegisterRequest(username = "newuser", password = "p@ssw0rd!")
        val str = request.toString()
        assertContains(str, "newuser")
        assertContains(str, "****")
        assertFalse(str.contains("p@ssw0rd!"))
    }
}
