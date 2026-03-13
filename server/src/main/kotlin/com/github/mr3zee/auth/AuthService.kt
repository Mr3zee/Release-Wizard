package com.github.mr3zee.auth

import com.github.mr3zee.AuthConfig
import org.slf4j.LoggerFactory
import java.security.MessageDigest

interface AuthService {
    fun validate(username: String, password: String): Boolean
    fun getUsername(): String
}

class ConfigAuthService(private val config: AuthConfig) : AuthService {
    private val log = LoggerFactory.getLogger(ConfigAuthService::class.java)

    override fun validate(username: String, password: String): Boolean {
        val usernameMatch = MessageDigest.isEqual(
            username.toByteArray(Charsets.UTF_8),
            config.username.toByteArray(Charsets.UTF_8),
        )
        val passwordMatch = MessageDigest.isEqual(
            password.toByteArray(Charsets.UTF_8),
            config.password.toByteArray(Charsets.UTF_8),
        )
        val result = usernameMatch && passwordMatch
        if (result) {
            log.info("Login succeeded for user '{}'", username)
        } else {
            log.warn("Login failed for user '{}'", username)
        }
        return result
    }

    override fun getUsername(): String = config.username
}
