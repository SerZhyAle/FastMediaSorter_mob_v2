package com.sza.fastmediasorter.domain.model

/**
 * Domain model representing the audit status of a single credential record (B5-T4/T5).
 *
 * @property credentialId UUID of the credential.
 * @property credentialType Protocol type, e.g. "SMB" or "SFTP".
 * @property label Human-readable label: "server:port (username)".
 * @property status Audit status — see [CredentialStatus].
 */
data class CredentialAuditEntry(
    val credentialId: String,
    val credentialType: String,
    val label: String,
    val status: CredentialStatus
)

/**
 * Audit status of a stored credential.
 *
 * - [ACTIVE]   — at least one resource references this credential.
 * - [ORPHANED] — stored but not referenced by any resource; safe to review/delete.
 */
enum class CredentialStatus {
    ACTIVE,
    ORPHANED
}

/**
 * Aggregate report returned by [com.sza.fastmediasorter.domain.usecase.CredentialAuditor.audit].
 *
 * @property entries  Per-credential audit results.
 * @property totalCount  Total credential count.
 * @property orphanedCount  Number of orphaned credentials.
 */
data class CredentialAuditReport(
    val entries: List<CredentialAuditEntry>,
    val totalCount: Int = entries.size,
    val orphanedCount: Int = entries.count { it.status == CredentialStatus.ORPHANED }
)
