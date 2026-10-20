package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.BackupExchangeAccount
import com.sza.fastmediasorter.domain.model.BackupPreference

/**
 * S3130: the settings store seen as entries rather than as a field list, so a backup carries every
 * setting - including ones added after this was written - without an edit in the backup code.
 *
 * Deliberately separate from [SettingsRepository], which speaks in the typed `AppSettings` snapshot:
 * the entry operations never read or write a named setting, and the two contracts share nothing.
 *
 * The exchange account entries live in the same store and are kept out of the entry loop (S4238), so
 * the two account operations below are the only door they leave and re-enter through.
 */
interface RawSettingsRepository {

    /** Every stored setting as a name-type-value triple. */
    suspend fun exportAll(): List<BackupPreference>

    /**
     * Writes [values] back by setting name. A name this build does not know is written as it came,
     * so a backup from another version restores what it can instead of failing.
     */
    suspend fun importAll(values: List<BackupPreference>)

    /** S4238: every exchange account entry as an address, a login and a pin - no token, no device id. */
    suspend fun exportExchangeAccounts(): List<BackupExchangeAccount>

    /**
     * S4238: puts [accounts] back as entries with a fresh device id and no token, so each one waits for the
     * account password. An identity this device already holds is left alone. Returns how many were added.
     */
    suspend fun importExchangeAccounts(accounts: List<BackupExchangeAccount>): Int
}
