package com.sza.fastmediasorter.data.migration

import android.content.Context
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0981 - run-once flip of `linkAutoDownloadOpenInPlayer` default from ON to OFF.
 *
 * The field's code default changed true -> false (owner call: a background-triggered player
 * launch can fail to visually snap to foreground, see LinkAutoDownloadResultPresenter.launchPlayer
 * KDoc), but `SettingsRepositoryImpl` round-trips the full `AppSettings` snapshot on every save, so
 * any install that has ever saved settings once already has `true` explicitly persisted in
 * DataStore - a code-default change alone would not reach them. This migration forces the
 * persisted value to `false` exactly once, mirroring S0386UpgradeReconciliation's accepted
 * trade-off: it cannot distinguish "still at the old default" from "user deliberately keeps it
 * ON", so it resets unconditionally, same as that precedent.
 *
 * Idempotent: a sentinel flag in `SharedPreferences("s0981_migration")` prevents re-execution, so
 * a user who re-enables the setting after this migration runs is never reset again.
 */
@Singleton
class S0981OpenInPlayerDefaultOff @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /** Idempotent. Safe to call from `Application.onCreate` every launch - early-exits once done. */
    // Catch-all mirrors S0386UpgradeReconciliation/S0200AuthStateWipe: a startup migration must
    // never crash the app on an unexpected DataStore/SharedPreferences failure - the sentinel is
    // only persisted on success, so any failure simply retries on the next launch.
    @Suppress("TooGenericExceptionCaught")
    suspend fun runIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        try {
            val settings = settingsRepository.getSettings().first()
            if (settings.linkAutoDownloadOpenInPlayer) {
                settingsRepository.updateSettings(settings.copy(linkAutoDownloadOpenInPlayer = false))
                Timber.i("Open-in-player default migration: forced linkAutoDownloadOpenInPlayer OFF")
            }

            // Persist sentinel LAST so a partial-failure run retries on next launch.
            prefs.edit().putBoolean(KEY_DONE, true).commit()
        } catch (t: Throwable) {
            Timber.e(t, "Open-in-player default migration failed; will retry on next launch")
        }
    }

    private companion object {
        const val PREFS = "s0981_migration"
        const val KEY_DONE = "open_in_player_default_off_done"
    }
}
