package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.SftpExchangeConfig
import com.sza.fastmediasorter.domain.model.SftpShareId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists what the embedded SFTP server publishes on the exchange (contract ANYWHERE-ACCESS section 5): the
 * publish switch and the share id. The server address, login and certificate pin belong to the outgoing
 * account entry in [DeviceExchangeAccountStore].
 *
 * Separate from [SftpServerSettingsStore] because none of it changes how the LAN server serves: with
 * the toggle off, nothing here is read by the server path at all.
 */
@Singleton
class SftpExchangeSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    val values: Flow<SftpExchangeConfig> = dataStore.data
        .map(::decode)
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    /**
     * The current configuration; generates and stores the share id the first time it is needed, and drops the
     * v1 server values in the same edit.
     */
    suspend fun snapshot(): SftpExchangeConfig {
        val stored = dataStore.data.first()
        if (SftpShareId.isValid(stored[keyShareId]) && !hasRetiredValues(stored)) return decode(stored)
        val generated = SftpShareId.generate()
        edit {
            removeRetiredValues(it)
            if (!SftpShareId.isValid(it[keyShareId])) it[keyShareId] = generated
        }
        return values.first()
    }

    suspend fun setEnabled(enabled: Boolean) = edit { it[keyEnabled] = enabled }

    /** Revokes every code shared so far: the old id stops opening tunnels (contract section 6.2). */
    suspend fun rotateShareId() = edit { it[keyShareId] = SftpShareId.generate() }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun decode(preferences: Preferences) = SftpExchangeConfig(
        enabled = preferences[keyEnabled] ?: false,
        shareId = preferences[keyShareId].orEmpty(),
    )

    private companion object {
        val keyEnabled = booleanPreferencesKey("sftp_exchange_enabled")
        val keyShareId = stringPreferencesKey("sftp_exchange_share_id")

        // The v1 exchange server (ANYWHERE-ACCESS 0.10) the SFTP card no longer edits since S4116. Nothing writes
        // these any more; they are deleted so the Keystore-encrypted password does not outlive its feature.
        val retiredHost = stringPreferencesKey("sftp_exchange_host")
        val retiredPort = intPreferencesKey("sftp_exchange_port")
        val retiredPasswordEncrypted = stringPreferencesKey("sftp_exchange_password_enc")
        val retiredCertificatePin = stringPreferencesKey("sftp_exchange_certificate_pin")

        fun hasRetiredValues(preferences: Preferences): Boolean =
            retiredHost in preferences || retiredPort in preferences ||
                retiredPasswordEncrypted in preferences || retiredCertificatePin in preferences

        fun removeRetiredValues(preferences: MutablePreferences) {
            preferences.remove(retiredHost)
            preferences.remove(retiredPort)
            preferences.remove(retiredPasswordEncrypted)
            preferences.remove(retiredCertificatePin)
        }
    }
}
