package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.CredentialAuditEntry
import com.sza.fastmediasorter.domain.model.CredentialStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy that determines when an orphaned credential is eligible for deletion (B5-T6).
 *
 * An orphaned credential (one with no associated resources) is considered eligible for
 * cleanup only after a configurable grace period. This prevents accidental deletion of
 * credentials that were temporarily unused due to a resource being removed and re-added.
 *
 * **Credentials are never auto-deleted** - this class only marks eligibility.
 * Actual deletion must be triggered explicitly (e.g., by a user action or scheduled job
 * after confirmation).
 *
 * @property gracePeriodMs How long (in ms) an orphaned credential is kept before it is
 *   considered eligible for cleanup. Default: 30 days.
 */
@Singleton
class UnusedCredentialPolicy @Inject constructor() {

    var gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS
        set(value) {
            require(value > 0) { "gracePeriodMs must be positive" }
            field = value
        }

    /**
     * Returns true when the credential should be offered for deletion:
     * - Its [entry.status] is [CredentialStatus.ORPHANED], AND
     * - It has been orphaned for more than [gracePeriodMs].
     *
     * S1649: the elapsed time is measured from [CredentialAuditEntry.orphanedSince], the moment the
     * credential was first seen unreferenced - not from when it was stored. Measuring from storage
     * time inverted the guarantee: a credential kept for a year lost its whole grace period the
     * instant its resource was deleted, while yesterday's was shielded for a month regardless.
     *
     * An unknown orphan moment is never eligible. Every row carries null right after the schema 51
     * migration until the background worker stamps it, and a missing clock must not read as an
     * expired one.
     *
     * @param entry   The credential audit entry to evaluate.
     * @param nowMs   Current epoch time in ms (injectable for testing).
     */
    fun isEligibleForCleanup(
        entry: CredentialAuditEntry,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (entry.status != CredentialStatus.ORPHANED) return false
        val orphanedSince = entry.orphanedSince ?: return false
        if (orphanedSince <= 0L) return false
        return (nowMs - orphanedSince) >= gracePeriodMs
    }

    /**
     * Filters [entries] to those eligible for cleanup according to the current policy.
     *
     * @param nowMs   Current epoch time in ms (injectable for testing).
     */
    fun filterEligible(
        entries: List<CredentialAuditEntry>,
        nowMs: Long = System.currentTimeMillis()
    ): List<CredentialAuditEntry> = entries.filter { isEligibleForCleanup(it, nowMs) }

    companion object {
        /** 30 days in milliseconds. */
        const val DEFAULT_GRACE_PERIOD_MS = 30L * 24 * 60 * 60 * 1000
    }
}
