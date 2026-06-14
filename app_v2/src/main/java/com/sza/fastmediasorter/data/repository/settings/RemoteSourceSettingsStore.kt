package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the S0391 per-source runtime toggles: SMB, SFTP, FTP and the three
 * cloud providers (Google Drive, OneDrive, Dropbox).
 *
 * Missing keys default to true so an upgrade keeps every source enabled - the gate
 * (RemoteSourceAvailabilityGate) folds these with compile-time support.
 */
object RemoteSourceSettingsStore {

    private val KEY_SMB_ENABLED = booleanPreferencesKey("source_smb_enabled")
    private val KEY_SFTP_ENABLED = booleanPreferencesKey("source_sftp_enabled")
    private val KEY_FTP_ENABLED = booleanPreferencesKey("source_ftp_enabled")
    private val KEY_GDRIVE_ENABLED = booleanPreferencesKey("source_gdrive_enabled")
    private val KEY_ONEDRIVE_ENABLED = booleanPreferencesKey("source_onedrive_enabled")
    private val KEY_DROPBOX_ENABLED = booleanPreferencesKey("source_dropbox_enabled")

    /** Per-source enabled flags read from DataStore, ready for [AppSettings]. */
    data class Values(
        val smbEnabled: Boolean,
        val sftpEnabled: Boolean,
        val ftpEnabled: Boolean,
        val googleDriveEnabled: Boolean,
        val oneDriveEnabled: Boolean,
        val dropboxEnabled: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        smbEnabled = preferences[KEY_SMB_ENABLED] ?: true,
        sftpEnabled = preferences[KEY_SFTP_ENABLED] ?: true,
        ftpEnabled = preferences[KEY_FTP_ENABLED] ?: true,
        googleDriveEnabled = preferences[KEY_GDRIVE_ENABLED] ?: true,
        oneDriveEnabled = preferences[KEY_ONEDRIVE_ENABLED] ?: true,
        dropboxEnabled = preferences[KEY_DROPBOX_ENABLED] ?: true,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_SMB_ENABLED] = settings.smbEnabled
        preferences[KEY_SFTP_ENABLED] = settings.sftpEnabled
        preferences[KEY_FTP_ENABLED] = settings.ftpEnabled
        preferences[KEY_GDRIVE_ENABLED] = settings.googleDriveEnabled
        preferences[KEY_ONEDRIVE_ENABLED] = settings.oneDriveEnabled
        preferences[KEY_DROPBOX_ENABLED] = settings.dropboxEnabled
    }
}
