package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountEntry
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
 * Persists the exchange server the embedded SFTP server registers on (contract ANYWHERE-ACCESS
 * section 5): the toggle, address, port, the server password encrypted with the Keystore-backed
 * [CryptoHelper], the share id and the pinned certificate fingerprint.
 *
 * Separate from [SftpServerSettingsStore] because none of it changes how the LAN server serves: with
 * the toggle off, nothing here is read by the server path at all.
 */
@Singleton
class SftpExchangeSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    // Narrowed to these keys and deduplicated before the Keystore decrypt, as in SftpServerSettingsStore:
    // an unrelated setting write must not re-run a binder call on the collector's thread.
    val values: Flow<SftpExchangeConfig> = dataStore.data
        .map(::readStored)
        .distinctUntilChanged()
        .map(::decode)
        .flowOn(ioDispatcher)

    /** The current configuration; generates and stores the share id the first time it is needed. */
    suspend fun snapshot(): SftpExchangeConfig {
        val current = values.first()
        if (SftpShareId.isValid(current.shareId)) return current
        val generated = SftpShareId.generate()
        edit { if (!SftpShareId.isValid(it[keyShareId])) it[keyShareId] = generated }
        return values.first()
    }

    /**
     * The account entry the share is published through (contract DEVICE-EXCHANGE 5.2), or null while no
     * login is set. Its `deviceId` is created once, before the first enrollment, and kept with the entry.
     * The `schemaVersion` 1 password is not part of it: the server password is never sent on the v2 wire.
     */
    suspend fun accountEntry(): DeviceExchangeAccountEntry? {
        val stored = dataStore.data.first()
        val host = stored[keyHost]?.takeIf(String::isNotBlank)
        val login = stored[keyLogin]?.takeIf(DeviceExchangeAccountEntry::isValidLogin)
        if (host == null || login == null) return null
        edit { if (!SftpShareId.isValid(it[keyDeviceId])) it[keyDeviceId] = SftpShareId.generate() }
        val deviceId = requireNotNull(dataStore.data.first()[keyDeviceId])
        PrivateDiagnosticValues.register(host, login, stored[keyPinnedCertificate])
        return DeviceExchangeAccountEntry(
            host = host,
            port = stored[keyPort] ?: SftpExchangeConfig.DEFAULT_PORT,
            login = login,
            deviceId = deviceId,
            pinnedCertificate = stored[keyPinnedCertificate],
        )
    }

    suspend fun setEnabled(enabled: Boolean) = edit { it[keyEnabled] = enabled }

    /** A new address is a new server: the old certificate pin would refuse it, so it is dropped. */
    suspend fun setServer(host: String, port: Int) {
        require(port in 1..SftpExchangeConfig.MAX_PORT) { "Exchange server port out of range: $port" }
        PrivateDiagnosticValues.register(host)
        edit {
            if (it[keyHost] != host.trim() || it[keyPort] != port) it.remove(keyPinnedCertificate)
            it[keyHost] = host.trim()
            it[keyPort] = port
        }
    }

    /** Returns false when the Keystore refused to encrypt, so the caller never believes a lost write. */
    suspend fun setPassword(password: String): Boolean {
        val encrypted = CryptoHelper.encrypt(password) ?: return false
        edit { it[keyPasswordEncrypted] = encrypted }
        return true
    }

    /** Revokes every code shared so far: the old id stops opening tunnels (contract section 6.2). */
    suspend fun rotateShareId() = edit { it[keyShareId] = SftpShareId.generate() }

    suspend fun pinCertificate(fingerprint: String) = edit { it[keyPinnedCertificate] = fingerprint }

    suspend fun clearCertificatePin() = edit { it.remove(keyPinnedCertificate) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    // Every read of the account values feeds the log redaction (DIAGNOSTIC-REPORT section 10), so a value
    // is known to the sink from the first time the app looks at it, not only from the first enrollment.
    private fun readStored(preferences: Preferences): StoredExchangeValues {
        PrivateDiagnosticValues.register(
            preferences[keyHost],
            preferences[keyLogin],
            preferences[keyPinnedCertificate],
        )
        return StoredExchangeValues(
            enabled = preferences[keyEnabled],
            host = preferences[keyHost],
            port = preferences[keyPort],
            passwordEncrypted = preferences[keyPasswordEncrypted],
            shareId = preferences[keyShareId],
            pinnedCertificate = preferences[keyPinnedCertificate],
        )
    }

    private fun decode(stored: StoredExchangeValues): SftpExchangeConfig {
        val password = stored.passwordEncrypted?.let(CryptoHelper::decrypt)?.takeUnless(String::isEmpty)
        PrivateDiagnosticValues.register(password)
        return SftpExchangeConfig(
            enabled = stored.enabled ?: false,
            host = stored.host.orEmpty(),
            port = stored.port ?: SftpExchangeConfig.DEFAULT_PORT,
            password = password,
            shareId = stored.shareId.orEmpty(),
            pinnedCertificate = stored.pinnedCertificate,
        )
    }

    private data class StoredExchangeValues(
        val enabled: Boolean?,
        val host: String?,
        val port: Int?,
        val passwordEncrypted: String?,
        val shareId: String?,
        val pinnedCertificate: String?,
    )

    private companion object {
        val keyEnabled = booleanPreferencesKey("sftp_exchange_enabled")
        val keyHost = stringPreferencesKey("sftp_exchange_host")
        val keyPort = intPreferencesKey("sftp_exchange_port")
        val keyPasswordEncrypted = stringPreferencesKey("sftp_exchange_password_enc")
        val keyShareId = stringPreferencesKey("sftp_exchange_share_id")
        val keyPinnedCertificate = stringPreferencesKey("sftp_exchange_certificate_pin")
        val keyLogin = stringPreferencesKey("sftp_exchange_login")
        val keyDeviceId = stringPreferencesKey("sftp_exchange_device_id")
    }
}
