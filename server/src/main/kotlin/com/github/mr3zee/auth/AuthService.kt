package com.github.mr3zee.auth

import com.github.mr3zee.AuthConfig
import java.security.MessageDigest

interface AuthService {
    fun validate(username: String, password: String): Boolean
    fun getUsername(): String
}

class ConfigAuthService(private val config: AuthConfig) : AuthService {
    override fun validate(username: String, password: String): Boolean {
        val usernameMatch = MessageDigest.isEqual(
            username.toByteArray(Charsets.UTF_8),
            config.username.toByteArray(Charsets.UTF_8),
        )
        val passwordMatch = MessageDigest.isEqual(
            password.toByteArray(Charsets.UTF_8),
            config.password.toByteArray(Charsets.UTF_8),
        )
        return usernameMatch && passwordMatch
    }

    override fun getUsername(): String = config.username
}
