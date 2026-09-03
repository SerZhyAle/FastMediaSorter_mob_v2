package com.sza.fastmediasorter.data.cloud.helpers

import com.sza.fastmediasorter.domain.identity.transfer.TransferredSecretRestorer
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a transferred Dropbox credential back into [DropboxCredentialsManager] (S2101).
 *
 * Restoration is silent on every branch: a payload written by a build that spelled its fields
 * differently, and a device that already signed in here, both end in the ordinary sign-in the user
 * would have been offered anyway.
 */
@Singleton
class DropboxTransferredSecretRestorer @Inject constructor(
    private val credentialsManager: DropboxCredentialsManager
) : TransferredSecretRestorer {

    override suspend fun restore(payload: Map<String, String>) {
        val credentialsJson = payload[TransferredCredentialPayload.CREDENTIALS]
        val accountEmail = payload[TransferredCredentialPayload.EMAIL]
        if (credentialsJson.isNullOrBlank()) {
            Timber.i("Transferred Dropbox entry carries no credential; nothing to restore")
            return
        }
        if (credentialsManager.loadStoredCredentials(accountEmail) != null) {
            // A credential established on this device outranks one carried from the old one.
            Timber.i("Dropbox already signed in on this device; keeping the local credential")
            return
        }
        credentialsManager.saveCredentials(credentialsJson, accountEmail)
        Timber.i("Restored the transferred Dropbox credential")
    }
}
