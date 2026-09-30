package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.model.SftpServerAuthMode
import com.sza.fastmediasorter.domain.model.SftpServerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the embedded SFTP server configuration: the enabled toggle, the listening port, the
 * authentication mode with its credential, and the ordered list of SAF tree URIs the server is
 * chrooted to.
 *
 * Kept out of `AppSettings` on purpose: nothing outside the server feature reads these values, and
 * the password must never travel through the general settings snapshot. The password is stored
 * encrypted with the Keystore-backed [CryptoHelper], the same protection remote-resource
 * credentials get.
 */
@Singleton
class SftpServerSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {

    // The DataStore is shared by every app setting, so the upstream is narrowed to the SFTP keys and
    // deduplicated before the Keystore decrypt: otherwise any unrelated setting write re-ran a binder
    // call on the collector's thread, which is the main thread in the settings panel.
    val values: Flow<SftpServerConfig> = dataStore.data
        .map(::readStored)
        .distinctUntilChanged()
        .map(::decode)
        .flowOn(ioDispatcher)

    suspend fun snapshot(): SftpServerConfig = values.first()

    suspend fun setEnabled(enabled: Boolean) = edit { it[keyEnabled] = enabled }

    suspend fun setPort(port: Int) {
        require(port in SftpServerConfig.MIN_PORT..SftpServerConfig.MAX_PORT) { "SFTP server port out of range: $port" }
        edit { it[keyPort] = port }
    }

    suspend fun setAuthMode(mode: SftpServerAuthMode) = edit { it[keyAuthMode] = mode.name }

    suspend fun setUsername(username: String) {
        require(username.isNotBlank()) { "SFTP server username must not be blank" }
        edit { it[keyUsername] = username.trim() }
    }

    /** Returns false when the Keystore refused to encrypt, so the caller never believes a lost write. */
    suspend fun setPassword(password: String): Boolean {
        val encrypted = CryptoHelper.encrypt(password) ?: return false
        edit { it[keyPasswordEncrypted] = encrypted }
        return true
    }

    suspend fun setAuthorizedKeys(lines: List<String>) = edit {
        it[keyAuthorizedKeys] = lines.map(String::trim).filter(String::isNotEmpty).joinToString(LIST_SEPARATOR)
    }

    suspend fun addRoot(treeUri: String) = edit { prefs ->
        val roots = decodeList(prefs[keyRootUris])
        if (treeUri !in roots) prefs[keyRootUris] = (roots + treeUri).joinToString(LIST_SEPARATOR)
    }

    suspend fun removeRoot(treeUri: String) = edit { prefs ->
        prefs[keyRootUris] = (decodeList(prefs[keyRootUris]) - treeUri).joinToString(LIST_SEPARATOR)
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun readStored(preferences: Preferences): StoredSftpValues = StoredSftpValues(
        enabled = preferences[keyEnabled],
        port = preferences[keyPort],
        authMode = preferences[keyAuthMode],
        username = preferences[keyUsername],
        passwordEncrypted = preferences[keyPasswordEncrypted],
        authorizedKeys = preferences[keyAuthorizedKeys],
        rootUris = preferences[keyRootUris],
    )

    private fun decode(stored: StoredSftpValues): SftpServerConfig = SftpServerConfig(
        enabled = stored.enabled ?: false,
        port = stored.port ?: SftpServerConfig.DEFAULT_PORT,
        authMode = stored.authMode
            ?.let { name -> SftpServerAuthMode.entries.firstOrNull { it.name == name } }
            ?: SftpServerAuthMode.PASSWORD,
        username = stored.username ?: DEFAULT_USERNAME,
        password = stored.passwordEncrypted
            ?.let(CryptoHelper::decrypt)
            ?.takeUnless(String::isEmpty),
        authorizedKeys = decodeList(stored.authorizedKeys),
        rootUris = decodeList(stored.rootUris),
    )

    private data class StoredSftpValues(
        val enabled: Boolean?,
        val port: Int?,
        val authMode: String?,
        val username: String?,
        val passwordEncrypted: String?,
        val authorizedKeys: String?,
        val rootUris: String?,
    )

    private fun decodeList(raw: String?): List<String> =
        raw?.split(LIST_SEPARATOR)?.filter(String::isNotEmpty).orEmpty()

    companion object {
        const val DEFAULT_USERNAME = "fms"

        // Neither a tree URI nor an authorized_keys line can contain a newline.
        private const val LIST_SEPARATOR = "\n"

        private val keyEnabled = booleanPreferencesKey("sftp_server_enabled")
        private val keyPort = intPreferencesKey("sftp_server_port")
        private val keyAuthMode = stringPreferencesKey("sftp_server_auth_mode")
        private val keyUsername = stringPreferencesKey("sftp_server_username")
        private val keyPasswordEncrypted = stringPreferencesKey("sftp_server_password_enc")
        private val keyAuthorizedKeys = stringPreferencesKey("sftp_server_authorized_keys")
        private val keyRootUris = stringPreferencesKey("sftp_server_root_uris")
    }
}
