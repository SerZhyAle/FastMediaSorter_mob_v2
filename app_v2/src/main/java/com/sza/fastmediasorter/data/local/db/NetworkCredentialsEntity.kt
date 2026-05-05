package com.sza.fastmediasorter.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import timber.log.Timber

/**
 * Room entity for storing network credentials (SMB/SFTP).
 * Credentials are stored separately from resources for security and reusability.
 * Password field is manually encrypted/decrypted using CryptoHelper before storing.
 */
@Entity(
    tableName = "network_credentials",
    indices = [
        Index(value = ["credentialId"], unique = true, name = "idx_credentials_credential_id"),
        Index(value = ["type", "server", "port"], name = "idx_credentials_type_server_port"),
        Index(value = ["accountId"], name = "idx_credentials_account_id")
    ]
)
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
    val accountId: String = "", // Used for Cloud Auth Multi-Account mapping (e.g. Google Drive / OneDrive email)
    // S0064: pipe-separated list of share names entered manually by the user for this server.
    // Stored on every credential entity for the same server so the history survives partial deletions.
    @ColumnInfo(name = "manual_share_names")
    val manualShareNames: String = "",
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
            when {
                // CryptoHelper returns null only on decryption error (e.g. Keystore key invalidated).
                // Do NOT fall back to raw encryptedPassword – sending ciphertext as a password
                // would silently fail auth (STATUS_LOGON_FAILURE) with no actionable log.
                decrypted == null -> {
                    Timber.e("Password decryption returned null for credentialId: $credentialId – Keystore key may be invalidated. Returning empty string.")
                    ""
                }
                // CryptoHelper returns "" for empty-string input (valid empty password stored).
                decrypted.isEmpty() -> {
                    if (encryptedPassword.isEmpty()) {
                        Timber.w("Empty password stored for credentialId: $credentialId")
                        ""
                    } else {
                        // Non-empty encryptedPassword decrypted to "" is unexpected – treat the
                        // stored value as plaintext (migration / pre-encryption legacy).
                        Timber.i("Decryption produced empty string for non-empty stored value – using plaintext fallback for credentialId: $credentialId")
                        encryptedPassword
                    }
                }
                else -> decrypted
            }
        } catch (e: IllegalArgumentException) {
            // Base64 decode error – password is plaintext (migration case, no Base64 prefix)
            Timber.i("Password is plaintext for credentialId: $credentialId (migration, storedLength=${encryptedPassword.length})")
            encryptedPassword
        } catch (e: Exception) {
            // Unexpected error – return empty rather than leaking encrypted bytes as password
            Timber.e(e, "Unexpected error in password getter for credentialId: $credentialId – returning empty string")
            ""
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
            accountId: String = "",
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
                sshPrivateKey = sshPrivateKey?.let { CryptoHelper.encrypt(it) },
                accountId = accountId
            )
        }
    }
}
