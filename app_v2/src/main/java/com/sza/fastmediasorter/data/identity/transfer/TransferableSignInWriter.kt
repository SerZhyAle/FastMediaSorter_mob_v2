package com.sza.fastmediasorter.data.identity.transfer

import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInStore
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-merge-write over [TransferableSignInStore], shared by every provider that contributes to the
 * transferable record (S2101).
 *
 * One record holds every provider's entry, so a provider writing its own entry must not overwrite
 * its neighbours'. Centralising the merge here is what stops that: with the logic copied into each
 * credential manager, a later edit to one copy silently turns partial success - which the strategic
 * spec calls normal - into total loss for whichever provider signed in first.
 */
@Singleton
class TransferableSignInWriter @Inject constructor(
    private val store: TransferableSignInStore
) {

    /** Adds or replaces [providerKey]'s entry, keeping every other provider's. */
    suspend fun putEntry(
        providerKey: String,
        kind: TransferableSignInRecord.Kind,
        payload: Map<String, String>
    ): Boolean {
        val entry = TransferableSignInRecord.Entry(providerKey, kind, payload)
        val merged = currentRecord().withEntry(entry).copy(writtenAt = Instant.now().toEpochMilli())
        val stored = store.save(merged)
        if (!stored) {
            // Not an error for the caller: the sign-in itself succeeded and only its migration to a
            // future device did not, which the user cannot act on and must not be told about.
            Timber.w("Transferable sign-in entry for %s was not stored", providerKey)
        }
        return stored
    }

    /**
     * Removes [providerKey]'s entry. Clears the whole record once nothing is left, so a fully
     * signed-out app leaves no stored bytes rather than an empty envelope.
     */
    suspend fun removeEntry(providerKey: String) {
        Timber.d("S2101: erasing transferable entry for $providerKey")
        val remaining = currentRecord().withoutProvider(providerKey)
        if (remaining.entries.isEmpty()) {
            store.clear()
        } else {
            store.save(remaining.copy(writtenAt = Instant.now().toEpochMilli()))
        }
    }

    private suspend fun currentRecord(): TransferableSignInRecord =
        store.readOnce() ?: TransferableSignInRecord.empty(Instant.now().toEpochMilli())
}
