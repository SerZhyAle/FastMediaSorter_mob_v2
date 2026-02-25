package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.CredentialAuditEntry
import com.sza.fastmediasorter.domain.model.CredentialAuditReport
import com.sza.fastmediasorter.domain.model.CredentialStatus
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audits stored network credentials for orphaned (unreferenced) entries.
 * Implements B5-T4 (CredentialAuditor) and B5-T5 (UI signal via [auditAsFlow]).
 *
 * This class does NOT modify any data — it is read-only and side-effect free.
 *
 * ### Usage
 * - One-shot check: `val report = credentialAuditor.audit()`
 * - Reactive (for UI): `credentialAuditor.auditAsFlow().collect { report -> … }`
 */
@Singleton
class CredentialAuditor @Inject constructor(
    private val credentialsRepository: NetworkCredentialsRepository
) {

    /**
     * Performs a one-shot credential audit.
     *
     * Fetches all stored credentials and cross-references them against the set of
     * orphaned ones (those with no associated resources).
     *
     * @return [CredentialAuditReport] summarising status per credential.
     */
    suspend fun audit(): CredentialAuditReport {
        val all      = credentialsRepository.getAllCredentials().first()
        val orphaned = credentialsRepository.getOrphanedCredentials()
        return buildReport(all, orphaned)
    }

    /**
     * Returns a [Flow] that emits a fresh [CredentialAuditReport] whenever the
     * credentials table changes. Intended as a reactive signal for the UI (B5-T5).
     *
     * Note: orphan status is re-evaluated on each emission via a suspend call to
     * [NetworkCredentialsRepository.getOrphanedCredentials].
     */
    fun auditAsFlow(): Flow<CredentialAuditReport> =
        credentialsRepository.getAllCredentials().map { all ->
            val orphaned = credentialsRepository.getOrphanedCredentials()
            buildReport(all, orphaned)
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun buildReport(
        all: List<NetworkCredentialsEntity>,
        orphaned: List<NetworkCredentialsEntity>
    ): CredentialAuditReport {
        val orphanedIds = orphaned.map { it.credentialId }.toHashSet()
        val entries = all.map { entity ->
            CredentialAuditEntry(
                credentialId   = entity.credentialId,
                credentialType = entity.type,
                label          = "${entity.server}:${entity.port} (${entity.username})",
                status         = if (entity.credentialId in orphanedIds) {
                    CredentialStatus.ORPHANED
                } else {
                    CredentialStatus.ACTIVE
                }
            )
        }
        val report = CredentialAuditReport(entries)
        logReport(report)
        return report
    }

    private fun logReport(report: CredentialAuditReport) {
        if (report.orphanedCount > 0) {
            val ids = report.entries
                .filter { it.status == CredentialStatus.ORPHANED }
                .joinToString { it.credentialId }
            Timber.w(
                "CredentialAuditor: ${report.orphanedCount}/${report.totalCount} " +
                    "credentials are ORPHANED (no associated resources). IDs: $ids"
            )
        } else {
            Timber.d("CredentialAuditor: ${report.totalCount} credentials audited — all ACTIVE")
        }
    }
}

