package com.sza.fastmediasorter.data.local.db

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for CryptoHelper - Android Keystore encryption/decryption
 * 
 * Uses Robolectric to simulate Android Keystore environment in JVM tests
 * Tests AES-256-GCM encryption with IV handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // minSdk = 28
class CryptoHelperTest {

    @Test
    fun `encrypt returns non-empty string for valid input`() {
        // Given
        val plaintext = "test_password_123"
        
        // When
        val encrypted = CryptoHelper.encrypt(plaintext)
        
        // Then
        assertNotNull("Encrypted result should not be null", encrypted)
        assertTrue("Encrypted string should not be empty", encrypted!!.isNotEmpty())
        assertNotEquals("Encrypted should differ from plaintext", plaintext, encrypted)
    }

    @Test
    fun `decrypt returns original plaintext after encryption`() {
        // Given
        val original = "my_secret_password"
        
        // When
        val encrypted = CryptoHelper.encrypt(original)
        val decrypted = CryptoHelper.decrypt(encrypted)
        
        // Then
        assertNotNull("Decrypted result should not be null", decrypted)
        assertEquals("Decrypted should match original", original, decrypted)
    }

    @Test
    fun `encrypt returns empty string for empty input`() {
        // Given
        val plaintext = ""
        
        // When
        val encrypted = CryptoHelper.encrypt(plaintext)
        
        // Then
        assertEquals("Empty input should return empty string", "", encrypted)
    }

    @Test
    fun `decrypt returns empty string for empty input`() {
        // Given
        val encrypted = ""
        
        // When
        val decrypted = CryptoHelper.decrypt(encrypted)
        
        // Then
        assertEquals("Empty encrypted input should return empty string", "", decrypted)
    }

    @Test
    fun `decrypt returns null for invalid Base64 input`() {
        // Given
        val invalidBase64 = "not_valid_base64!@#$%"
        
        // When
        val decrypted = CryptoHelper.decrypt(invalidBase64)
        
        // Then
        assertNull("Invalid Base64 should return null", decrypted)
    }

    @Test
    fun `decrypt returns null for corrupted encrypted data`() {
        // Given
        val plaintext = "test_data"
        val encrypted = CryptoHelper.encrypt(plaintext)!!
        
        // Corrupt encrypted data by changing one character
        val corrupted = encrypted.replaceFirst('A', 'B')
        
        // When
        val decrypted = CryptoHelper.decrypt(corrupted)
        
        // Then
        assertNull("Corrupted encrypted data should return null", decrypted)
    }

    @Test
    fun `encrypt produces different output for same input due to random IV`() {
        // Given
        val plaintext = "same_password"
        
        // When
        val encrypted1 = CryptoHelper.encrypt(plaintext)
        val encrypted2 = CryptoHelper.encrypt(plaintext)
        
        // Then
        assertNotNull(encrypted1)
        assertNotNull(encrypted2)
        assertNotEquals(
            "Same plaintext should produce different ciphertext due to random IV",
            encrypted1,
            encrypted2
        )
        
        // But both should decrypt to same plaintext
        assertEquals(plaintext, CryptoHelper.decrypt(encrypted1))
        assertEquals(plaintext, CryptoHelper.decrypt(encrypted2))
    }

    @Test
    fun `encrypt handles unicode characters correctly`() {
        // Given
        val plaintext = "Тест пароль 测试密码 🔒🔑"
        
        // When
        val encrypted = CryptoHelper.encrypt(plaintext)
        val decrypted = CryptoHelper.decrypt(encrypted)
        
        // Then
        assertNotNull("Encrypted unicode should not be null", encrypted)
        assertEquals("Unicode characters should decrypt correctly", plaintext, decrypted)
    }

    @Test
    fun `encrypt handles long strings correctly`() {
        // Given
        val plaintext = "a".repeat(10000) // 10KB string
        
        // When
        val encrypted = CryptoHelper.encrypt(plaintext)
        val decrypted = CryptoHelper.decrypt(encrypted)
        
        // Then
        assertNotNull("Long string encryption should not fail", encrypted)
        assertEquals("Long string should decrypt correctly", plaintext, decrypted)
    }

    @Test
    fun `encrypt handles special characters correctly`() {
        // Given
        val plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        
        // When
        val encrypted = CryptoHelper.encrypt(plaintext)
        val decrypted = CryptoHelper.decrypt(encrypted)
        
        // Then
        assertNotNull("Special characters encryption should not fail", encrypted)
        assertEquals("Special characters should decrypt correctly", plaintext, decrypted)
    }

    @Test
    fun `decrypt returns null for null input`() {
        // When
        val decrypted = CryptoHelper.decrypt(null)
        
        // Then
        assertEquals("Null input should return empty string", "", decrypted)
    }

    @Test
    fun `multiple encrypt-decrypt cycles maintain data integrity`() {
        // Given
        var data = "original_password"
        
        // When - encrypt and decrypt 5 times
        repeat(5) {
            val encrypted = CryptoHelper.encrypt(data)
            data = CryptoHelper.decrypt(encrypted)!!
        }
        
        // Then
        assertEquals("Multiple cycles should maintain integrity", "original_password", data)
    }
}
