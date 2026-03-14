package com.github.mr3zee.auth

import com.github.mr3zee.PasswordPolicyConfig

class PasswordValidator(private val config: PasswordPolicyConfig) {

    companion object {
        /** Hard ceiling to prevent Argon2 DoS with extremely long passwords */
        const val MAX_PASSWORD_LENGTH = 128
    }

    fun validate(password: String): List<String> {
        val errors = mutableListOf<String>()
        if (password.length > MAX_PASSWORD_LENGTH) {
            errors += "Password must not exceed $MAX_PASSWORD_LENGTH characters"
        }
        if (password.length < config.minLength) {
            errors += "Password must be at least ${config.minLength} characters"
        }
        if (config.requireUppercase && password.none { it.isUpperCase() }) {
            errors += "Password must contain at least one uppercase letter"
        }
        if (config.requireDigit && password.none { it.isDigit() }) {
            errors += "Password must contain at least one digit"
        }
        if (config.requireSpecial && password.none { !it.isLetterOrDigit() }) {
            errors += "Password must contain at least one special character"
        }
        return errors
    }
}
