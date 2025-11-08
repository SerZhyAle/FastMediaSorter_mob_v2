package com.sza.fastmediasorter_v2.data.local.db

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
    val createdDate: Long = System.currentTimeMillis()
) {
    /**
     * Returns decrypted password for use in app.
     * Use this property instead of accessing encryptedPassword directly.
     */
    @get:Ignore
    val password: String
        get() = try {
            val decrypted = CryptoHelper.decrypt(encryptedPassword)
            if (decrypted.isNullOrEmpty()) {
                // Check if encryptedPassword is actually plaintext (for migration)
                if (encryptedPassword.isNotEmpty() && !encryptedPassword.contains(Regex("[^A-Za-z0-9+/=]"))) {
                    // Looks like Base64, but decryption failed - might be corrupted
                    Timber.w("Password decryption failed for credentialId: $credentialId, returning empty")
                    ""
                } else {
                    // Assume it's plaintext (migration from old version)
                    Timber.i("Using plaintext password for credentialId: $credentialId (migration)")
                    encryptedPassword
                }
            } else {
                decrypted
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt password for credentialId: $credentialId")
            // Fallback: assume it's plaintext
            Timber.i("Treating password as plaintext due to decryption error for credentialId: $credentialId")
            encryptedPassword
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
                shareName = shareName
            )
        }
    }
}
