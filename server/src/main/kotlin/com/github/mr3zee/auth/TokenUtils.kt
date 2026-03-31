package com.github.mr3zee.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Shared cryptographic token utilities for PATs, password reset tokens, etc.
 */
object TokenUtils {
    private val secureRandom = SecureRandom()

    fun generateRawToken(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
