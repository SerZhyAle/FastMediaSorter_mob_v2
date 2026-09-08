package com.sza.fastmediasorter.domain.usecase.apps

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S2736: moves an install that still stores the pre-S2736 default onto the launch-frequency order.
 *
 * A plain default change would reach nobody who already has the app: `LauncherSettingsStore` writes
 * every launcher key on any settings save, so an existing install stores `LABEL` explicitly and the
 * declared default is never read again. An order the user picked himself is left alone - only the
 * value the old default wrote is rewritten, and only once.
 */
class ApplyAllAppsSortDefaultUseCase @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke() {
        if (dataStore.data.first()[KEY_DONE] == true) return
        val stored = settingsRepository.getSettings().first().allAppsSortOrder
        Timber.d("S2736: all-apps sort default migration sees stored order %s", stored)
        if (stored == InstalledAppSortOrder.LABEL.name) {
            settingsRepository.updateSettings { settings ->
                settings.withLauncher { copy(allAppsSortOrder = InstalledAppSortOrder.LAUNCH_FREQUENCY.name) }
            }
        }
        // Marked done in both branches: the question "was the old default still stored" is asked once,
        // or a user who picks the alphabet afterwards would be moved off it on the next launch.
        dataStore.edit { it[KEY_DONE] = true }
    }

    private companion object {
        val KEY_DONE = booleanPreferencesKey("all_apps_sort_default_frequency_done")
    }
}
