package com.github.mr3zee.security

import com.github.mr3zee.EncryptionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EncryptionServiceTest {

    private val validKey = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
    private val differentKey = "MTIzNDU2Nzhhc2RmZ2hqa2xxd2VydHl1aW9wem14Y3Y="

    private fun service(key: String = validKey) = EncryptionService(EncryptionConfig(key = key))

    @Test
    fun `encrypt then decrypt produces original value`() {
        val svc = service()
        val original = "Hello, World! Secret data 12345"
        val encrypted = svc.encrypt(original)
        val decrypted = svc.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypted value differs from plaintext`() {
        val svc = service()
        val original = "secret"
        val encrypted = svc.encrypt(original)
        assertNotEquals(original, encrypted)
    }

    @Test
    fun `decryption with wrong key fails`() {
        val svc1 = service(validKey)
        val svc2 = service(differentKey)
        val encrypted = svc1.encrypt("secret data")
        assertFailsWith<Exception> {
            svc2.decrypt(encrypted)
        }
    }

    @Test
    fun `empty string encrypts and decrypts correctly`() {
        val svc = service()
        val encrypted = svc.encrypt("")
        val decrypted = svc.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun `short input encrypts and decrypts correctly`() {
        val svc = service()
        val original = "a"
        val encrypted = svc.encrypt(original)
        val decrypted = svc.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `long input encrypts and decrypts correctly`() {
        val svc = service()
        val original = "x".repeat(10_000)
        val encrypted = svc.encrypt(original)
        val decrypted = svc.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `unicode input encrypts and decrypts correctly`() {
        val svc = service()
        val original = "Special chars: ghp_token, Webhook\$\$, key=value&more"
        val encrypted = svc.encrypt(original)
        val decrypted = svc.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `same plaintext produces different ciphertexts due to random IV`() {
        val svc = service()
        val plaintext = "same input"
        val enc1 = svc.encrypt(plaintext)
        val enc2 = svc.encrypt(plaintext)
        assertNotEquals(enc1, enc2, "Each encryption should produce unique ciphertext due to random IV")
        // But both should decrypt to the same value
        assertEquals(plaintext, svc.decrypt(enc1))
        assertEquals(plaintext, svc.decrypt(enc2))
    }

    @Test
    fun `invalid key length throws`() {
        assertFailsWith<IllegalArgumentException> {
            EncryptionService(EncryptionConfig(key = "c2hvcnQ=")) // "short" base64
        }
    }

    @Test
    fun `corrupted ciphertext fails decryption`() {
        val svc = service()
        val encrypted = svc.encrypt("test data")
        // Corrupt a character in the middle
        val corrupted = encrypted.substring(0, 10) + "X" + encrypted.substring(11)
        assertFailsWith<Exception> {
            svc.decrypt(corrupted)
        }
    }
}
