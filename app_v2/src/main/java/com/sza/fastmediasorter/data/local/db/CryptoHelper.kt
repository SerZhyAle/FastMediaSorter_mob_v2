package com.sza.fastmediasorter.data.local.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Helper class for encrypting/decrypting sensitive data using Android Keystore.
 * Uses AES-256-GCM encryption with hardware-backed key storage.
 */
object CryptoHelper {
    private const val KEY_ALIAS = "FastMediaSorter_Credentials_Key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    
    /**
     * Encrypts plaintext string using Android Keystore.
     * @param plaintext String to encrypt
     * @return Base64-encoded encrypted data with IV prepended, or null on error
     */
    fun encrypt(plaintext: String): String? {
        if (plaintext.isEmpty()) return ""
        
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            
            // Prepend IV to encrypted data: [IV_LENGTH][IV][ENCRYPTED_DATA]
            val combined = ByteArray(1 + iv.size + encryptedBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, 1 + iv.size, encryptedBytes.size)
            
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed")
            return null
        }
    }
    
    /**
     * Decrypts Base64-encoded string using Android Keystore.
     * @param encrypted Base64-encoded encrypted data with IV prepended
     * @return Decrypted plaintext string, or null on error
     */
    fun decrypt(encrypted: String?): String? {
        if (encrypted.isNullOrEmpty()) return ""
        
        try {
            val combined = Base64.decode(encrypted, Base64.NO_WRAP)
            
            // Extract IV: [IV_LENGTH][IV][ENCRYPTED_DATA]
            val ivLength = combined[0].toInt()
            val iv = ByteArray(ivLength)
            System.arraycopy(combined, 1, iv, 0, ivLength)
            
            val encryptedBytes = ByteArray(combined.size - 1 - ivLength)
            System.arraycopy(combined, 1 + ivLength, encryptedBytes, 0, encryptedBytes.size)
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed")
            return null
        }
    }
    
    /**
     * Gets existing key or creates new one in Android Keystore.
     * Key is hardware-backed on devices with Trusted Execution Environment (TEE).
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        // Check if key already exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        
        // Create new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // No user auth needed
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
