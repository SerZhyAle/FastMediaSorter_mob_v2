package com.sza.fastmediasorter.data.cloud.helpers

import com.sza.fastmediasorter.domain.identity.transfer.TransferredSecretRestorer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a transferred Google Drive browser-route credential back into
 * [GoogleDriveCredentialsManager] (S2101).
 *
 * Only the browser OAuth fallback stores a refresh token of its own; the GMS route mints tokens
 * against the system account, which migrates without help from this class.
 */
@Singleton
class GoogleDriveTransferredSecretRestorer @Inject constructor(
    private val credentialsManager: GoogleDriveCredentialsManager
) : TransferredSecretRestorer {

    override suspend fun restore(payload: Map<String, String>) {
        val credentialsJson = payload[TransferredCredentialPayload.CREDENTIALS]
        val accountEmail = payload[TransferredCredentialPayload.EMAIL]
        if (credentialsJson.isNullOrBlank()) {
            Timber.i("Transferred Google Drive entry carries no credential; nothing to restore")
            return
        }
        if (credentialsManager.loadStoredCredentials(accountEmail) != null) {
            // A credential established on this device outranks one carried from the old one.
            Timber.i("Google Drive already signed in on this device; keeping the local credential")
            return
        }
        credentialsManager.saveCredentials(credentialsJson, accountEmail)
        Timber.i("Restored the transferred Google Drive credential")
    }
}
