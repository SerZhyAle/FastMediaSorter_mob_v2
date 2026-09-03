package com.sza.fastmediasorter.domain.identity.transfer

import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Restores sign-in state left by a previous device, once, at application start (S2101).
 *
 * Silent by contract: it produces no dialog, no toast and no string on any branch. Nothing to
 * restore, an unavailable mechanism and an unreadable record are all the same outcome here - the
 * user is simply offered the ordinary sign-in, which is what they would see anyway.
 *
 * Each entry is restored inside its own error boundary. Partial success is normal: one provider
 * arriving and two not is a better result than abandoning all three because the first one failed.
 */
@Singleton
class RestoreTransferredSignInUseCase @Inject constructor(
    private val store: TransferableSignInStore,
    private val identityRepository: GoogleIdentityRepository,
    private val secretRestorers: Map<String, @JvmSuppressWildcards TransferredSecretRestorer>
) {

    suspend operator fun invoke() {
        val record = store.readOnce()
        if (record == null) {
            Timber.d("No transferred sign-in record to restore")
            return
        }
        record.entries.forEach { entry ->
            runCatching { restore(entry) }
                .onFailure { Timber.w(it, "Could not restore transferred entry for %s", entry.providerKey) }
        }
    }

    private suspend fun restore(entry: TransferableSignInRecord.Entry) {
        when (entry.kind) {
            TransferableSignInRecord.Kind.IDENTITY_ENVELOPE -> restoreEnvelope(entry)
            // An unknown provider key belongs to a build that knew a provider this one does not, so
            // there is no restorer for it and skipping is the whole correct action.
            TransferableSignInRecord.Kind.SECRET -> secretRestorers[entry.providerKey]?.restore(entry.payload)
        }
    }

    private suspend fun restoreEnvelope(entry: TransferableSignInRecord.Entry) {
        if (entry.providerKey != TransferableSignInProviderKeys.GOOGLE_PRIMARY) {
            return
        }
        val email = entry.payload[PAYLOAD_EMAIL] ?: return
        val scopes = entry.payload[PAYLOAD_SCOPES]
            ?.split(SCOPE_SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.map(::GoogleScope)
            ?.toSet()
            .orEmpty()
        identityRepository.restoreTransferredBinding(email, scopes)
    }

    private companion object {
        const val PAYLOAD_EMAIL = "email"
        const val PAYLOAD_SCOPES = "scopes"
        const val SCOPE_SEPARATOR = ","
    }
}
