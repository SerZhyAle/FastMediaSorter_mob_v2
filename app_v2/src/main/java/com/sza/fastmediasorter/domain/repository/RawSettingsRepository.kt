package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.BackupPreference

/**
 * S3130: the settings store seen as entries rather than as a field list, so a backup carries every
 * setting - including ones added after this was written - without an edit in the backup code.
 *
 * Deliberately separate from [SettingsRepository], which speaks in the typed `AppSettings` snapshot:
 * these two operations never read or write a named setting, and the two contracts share nothing.
 */
interface RawSettingsRepository {

    /** Every stored setting as a name-type-value triple. */
    suspend fun exportAll(): List<BackupPreference>

    /**
     * Writes [values] back by setting name. A name this build does not know is written as it came,
     * so a backup from another version restores what it can instead of failing.
     */
    suspend fun importAll(values: List<BackupPreference>)
}
