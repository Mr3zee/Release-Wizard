package com.github.mr3zee.auth

import com.github.mr3zee.PasswordPolicyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordValidatorTest {

    @Test
    fun `password at exact minimum length passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 8, requireUppercase = false, requireDigit = false, requireSpecial = false,
        ))
        val errors = validator.validate("abcdefgh") // exactly 8 chars
        assertTrue(errors.isEmpty(), "Password at exact min length should pass: $errors")
    }

    @Test
    fun `password one char below minimum length fails`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 8, requireUppercase = false, requireDigit = false, requireSpecial = false,
        ))
        val errors = validator.validate("abcdefg") // 7 chars
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("at least 8 characters"))
    }

    @Test
    fun `uppercase required and missing fails`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = true, requireDigit = false, requireSpecial = false,
        ))
        val errors = validator.validate("alllowercase")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("uppercase"))
    }

    @Test
    fun `uppercase required and present passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = true, requireDigit = false, requireSpecial = false,
        ))
        val errors = validator.validate("hasUppercase")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `digit required and missing fails`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = true, requireSpecial = false,
        ))
        val errors = validator.validate("nodigitshere")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("digit"))
    }

    @Test
    fun `digit required and present passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = true, requireSpecial = false,
        ))
        val errors = validator.validate("has1digit")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `special character required and missing fails`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = false, requireSpecial = true,
        ))
        val errors = validator.validate("nospecialchars123")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("special"))
    }

    @Test
    fun `special character required and present passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = false, requireSpecial = true,
        ))
        val errors = validator.validate("has@special")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `multiple requirements can fail simultaneously`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 20, requireUppercase = true, requireDigit = true, requireSpecial = true,
        ))
        val errors = validator.validate("short")
        assertTrue(errors.size >= 3, "Should have at least 3 errors: $errors")
    }

    @Test
    fun `extremely long password exceeding max length fails`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = false, requireSpecial = false,
        ))
        val longPassword = "A".repeat(PasswordValidator.MAX_PASSWORD_LENGTH + 1)
        val errors = validator.validate(longPassword)
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("must not exceed"))
    }

    @Test
    fun `password at exact max length passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 1, requireUppercase = false, requireDigit = false, requireSpecial = false,
        ))
        val maxPassword = "a".repeat(PasswordValidator.MAX_PASSWORD_LENGTH)
        val errors = validator.validate(maxPassword)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `all requirements met passes`() {
        val validator = PasswordValidator(PasswordPolicyConfig(
            minLength = 12, requireUppercase = true, requireDigit = true, requireSpecial = true,
        ))
        val errors = validator.validate("ValidPass123!")
        assertTrue(errors.isEmpty(), "Should pass all requirements: $errors")
    }
}
