package com.sza.fastmediasorter.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.data.repository.settings.DeviceExchangeAccountStore
import com.sza.fastmediasorter.data.repository.settings.RawPreferencesStore
import com.sza.fastmediasorter.domain.model.BackupExchangeAccount
import com.sza.fastmediasorter.domain.model.BackupPreference
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpShareId
import com.sza.fastmediasorter.domain.repository.RawSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** S3130: reads and writes the same settings DataStore [SettingsRepositoryImpl] owns, entry by entry. */
@Singleton
class RawSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val accountStore: DeviceExchangeAccountStore
) : RawSettingsRepository {

    override suspend fun exportAll(): List<BackupPreference> =
        RawPreferencesStore.export(dataStore.data.first())

    override suspend fun importAll(values: List<BackupPreference>) {
        if (values.isEmpty()) return
        dataStore.edit { preferences -> RawPreferencesStore.apply(preferences, values) }
    }

    override suspend fun exportExchangeAccounts(): List<BackupExchangeAccount> =
        accountStore.snapshot().map { entry ->
            BackupExchangeAccount(
                host = entry.host,
                port = entry.port,
                login = entry.login,
                pinnedCertificate = entry.pinnedCertificate,
                autoAttach = entry.autoAttach,
                outgoing = entry.outgoing
            )
        }

    override suspend fun importExchangeAccounts(accounts: List<BackupExchangeAccount>): Int {
        var restored = 0
        accounts.forEach { backup ->
            // `as? String`: Gson leaves a non-null field null for a payload that predates it.
            val host = (backup.host as? String)?.trim().orEmpty()
            val login = backup.login as? String
            if (host.isEmpty() || backup.port <= 0 || !DeviceExchangeAccountEntry.isValidLogin(login)) {
                return@forEach
            }
            val entry = DeviceExchangeAccountEntry(
                host = host,
                port = backup.port,
                login = checkNotNull(login),
                deviceId = SftpShareId.generate(),
                pinnedCertificate = backup.pinnedCertificate,
                autoAttach = backup.autoAttach,
                // Putting an outgoing entry clears the flag from every other one, so a restore must never
                // take the role from an entry the user already chose on this device.
                outgoing = backup.outgoing && accountStore.outgoing() == null
            )
            if (accountStore.entry(entry.identity) == null) {
                accountStore.put(entry)
                restored++
            }
        }
        return restored
    }
}
