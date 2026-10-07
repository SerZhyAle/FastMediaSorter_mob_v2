package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.SftpShareId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ids this installation writes into the Drive channel of contract DEVICE-EXCHANGE: one `deviceId` for
 * the life of the installation, and one `resourceId` for the life of the embedded server's share. Both
 * take the share id form (16 CSPRNG bytes, base64url) and survive restarts, so a consumer's attached
 * resource keeps pointing at the same records (section 6.2).
 */
@Singleton
class SftpRendezvousIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** The share's [resourceId]; [retiredResourceId] is the id of the share this one replaced, if any. */
    data class ResourceBinding(val resourceId: String, val retiredResourceId: String?)

    suspend fun deviceId(): String {
        dataStore.data.first()[keyDeviceId]?.let { return it }
        dataStore.edit { if (it[keyDeviceId] == null) it[keyDeviceId] = SftpShareId.generate() }
        return requireNotNull(dataStore.data.first()[keyDeviceId])
    }

    /**
     * The resource id of the share served under host key [fingerprint]. A new host key is a new share, so it
     * gets a new id and the old one is handed back for its record to be deleted.
     */
    suspend fun resourceIdFor(fingerprint: String): ResourceBinding {
        var retired: String? = null
        dataStore.edit {
            val current = it[keyResourceId]
            if (current == null || it[keyResourceFingerprint] != fingerprint) {
                retired = current
                it[keyResourceId] = SftpShareId.generate()
                it[keyResourceFingerprint] = fingerprint
            }
        }
        return ResourceBinding(requireNotNull(dataStore.data.first()[keyResourceId]), retired)
    }

    private companion object {
        val keyDeviceId = stringPreferencesKey("sftp_rendezvous_device_id")
        val keyResourceId = stringPreferencesKey("sftp_rendezvous_resource_id")
        val keyResourceFingerprint = stringPreferencesKey("sftp_rendezvous_resource_fingerprint")
    }
}
