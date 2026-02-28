package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores OAuth tokens that failed to be revoked server-side during sign-out.
 * WorkManager retries the revocation until acknowledged by the provider (B5-T3).
 */
@Entity(
    tableName = "pending_revocations",
    indices = [Index(value = ["provider"])]
)
data class PendingRevocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Provider identifier: "google", "onedrive", "dropbox" */
    val provider: String,
    /** The OAuth2 access (or refresh) token to revoke */
    val token: String,
    /** Full URL for the revoke HTTP request */
    val revokeUrl: String,
    /** Epoch ms when this record was created */
    val createdAt: Long = System.currentTimeMillis(),
    /** How many revocation attempts have been made so far */
    val attemptCount: Int = 0
)
