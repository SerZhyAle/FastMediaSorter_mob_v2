package com.sza.fastmediasorter.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import timber.log.Timber

/**
 * Room entity for storing network credentials (SMB/SFTP).
 * Credentials are stored separately from resources for security and reusability.
 * Password field is manually encrypted/decrypted using CryptoHelper before storing.
 */
@Entity(tableName = "network_credentials")
data class NetworkCredentialsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val credentialId: String, // Unique identifier (UUID)
    val type: String, // "SMB" or "SFTP"
    val server: String, // Server address or IP
    val port: Int, // Default: 445 for SMB, 22 for SFTP
    val username: String,
    
    @ColumnInfo(name = "password")
    val encryptedPassword: String, // Stored encrypted, must be decrypted via CryptoHelper
    
    val domain: String = "", // For SMB domain authentication
    val shareName: String? = null, // For SMB: share name
    val sshPrivateKey: String? = null, // For SFTP: SSH private key (encrypted, PEM format)
    val createdDate: Long = System.currentTimeMillis()
) {
    /**
     * Returns decrypted password for use in app.
     * Use this property instead of accessing encryptedPassword directly.
     */
    @get:Ignore
    val password: String
        get() = try {
            // Try to decrypt assuming it's Base64-encoded
            val decrypted = CryptoHelper.decrypt(encryptedPassword)
            if (decrypted.isNullOrEmpty()) {
                // Decryption returned empty - check if stored value is also empty
                Timber.w("Password decryption returned empty for credentialId: $credentialId, encryptedPassword='$encryptedPassword' (length=${encryptedPassword.length})")
                // If encryptedPassword is empty, decryption is correct (empty password stored)
                // If encryptedPassword is not empty, it's plaintext - use it
                if (encryptedPassword.isEmpty()) {
                    Timber.e("Empty password stored for user '$username' - SMB authentication will fail!")
                    ""
                } else {
                    Timber.i("Using plaintext password fallback")
                    encryptedPassword
                }
            } else {
                decrypted
            }
        } catch (e: IllegalArgumentException) {
            // Base64 decode error - password is plaintext (migration case)
            Timber.i("Password is plaintext for credentialId: $credentialId (migration), password='$encryptedPassword' (length=${encryptedPassword.length})")
            encryptedPassword
        } catch (e: Exception) {
            // Other decryption errors - treat as plaintext fallback
            Timber.e(e, "Decryption failed for credentialId: $credentialId, treating as plaintext")
            encryptedPassword
        }
    
    /**
     * Returns decrypted SSH private key for use in app.
     * Use this property instead of accessing sshPrivateKey directly.
     */
    @get:Ignore
    val decryptedSshPrivateKey: String?
        get() = sshPrivateKey?.let { encrypted ->
            try {
                CryptoHelper.decrypt(encrypted)
            } catch (e: Exception) {
                Timber.e(e, "SSH private key decryption failed for credentialId: $credentialId")
                null
            }
        }
    
    companion object {
        /**
         * Creates entity with encrypted password.
         * Use this factory method instead of constructor when storing passwords.
         */
        fun create(
            credentialId: String,
            type: String,
            server: String,
            port: Int,
            username: String,
            plaintextPassword: String,
            domain: String = "",
            shareName: String? = null,
            sshPrivateKey: String? = null,
            id: Long = 0
        ): NetworkCredentialsEntity {
            return NetworkCredentialsEntity(
                id = id,
                credentialId = credentialId,
                type = type,
                server = server,
                port = port,
                username = username,
                encryptedPassword = CryptoHelper.encrypt(plaintextPassword) ?: "",
                domain = domain,
                shareName = shareName,
                sshPrivateKey = sshPrivateKey?.let { CryptoHelper.encrypt(it) }
            )
        }
    }
}
