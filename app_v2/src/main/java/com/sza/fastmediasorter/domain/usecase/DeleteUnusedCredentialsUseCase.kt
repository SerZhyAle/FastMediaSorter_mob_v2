package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1649: deletes the stored credentials the audit marks eligible for cleanup.
 *
 * The set is taken from [CredentialAuditor], never re-queried from the DAO. Two implementations of
 * "orphaned" already existed in this app and had drifted - the background worker warned about every
 * unreferenced credential because it knew nothing about the grace period - so this use case reads the
 * one audit rather than adding a third answer.
 *
 * Deletion happens only on an explicit human action: a password is not recoverable, and the cost of a
 * wrong deletion is asymmetrically higher than the cost of a spare row, so nothing here is wired to a
 * background schedule.
 */
@Singleton
class DeleteUnusedCredentialsUseCase @Inject constructor(
    private val credentialAuditor: CredentialAuditor,
    private val credentialsRepository: NetworkCredentialsRepository
) {

    /**
     * Deletes every credential the current audit marks eligible for cleanup.
     *
     * @return how many credentials were actually deleted.
     */
    suspend fun deleteAllEligible(): Int {
        val eligible = credentialAuditor.audit().entries.filter { it.eligibleForCleanup }
        return deleteByIds(eligible.map { it.credentialId })
    }

    /**
     * Deletes the named credentials, but only those the current audit marks eligible.
     *
     * An id the audit does not mark is skipped and logged rather than deleted: the caller's list is a
     * user selection made against a screen that may be seconds out of date, and re-checking against a
     * fresh audit is what keeps a credential re-attached in the meantime from being removed anyway.
     *
     * @return how many credentials were actually deleted.
     */
    suspend fun delete(credentialIds: Collection<String>): Int {
        if (credentialIds.isEmpty()) {
            return 0
        }
        val eligibleIds = credentialAuditor.audit().entries
            .filter { it.eligibleForCleanup }
            .map { it.credentialId }
            .toHashSet()
        val requested = credentialIds.toSet()
        val refused = requested - eligibleIds
        if (refused.isNotEmpty()) {
            Timber.w(
                "DeleteUnusedCredentials: %d of %d requested id(s) are no longer eligible - skipping them",
                refused.size,
                requested.size
            )
        }
        return deleteByIds(requested.filter { it in eligibleIds })
    }

    private suspend fun deleteByIds(credentialIds: Collection<String>): Int {
        var deleted = 0
        for (credentialId in credentialIds) {
            val entity = credentialsRepository.getByCredentialId(credentialId)
            if (entity == null) {
                Timber.w("DeleteUnusedCredentials: credential %s vanished before deletion", credentialId)
                continue
            }
            credentialsRepository.delete(entity)
            deleted++
        }
        if (deleted > 0) {
            Timber.i("DeleteUnusedCredentials: deleted %d unused credential(s)", deleted)
        }
        return deleted
    }
}
