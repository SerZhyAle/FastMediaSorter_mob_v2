package com.sza.fastmediasorter.core.init

import android.content.Context
import com.sza.fastmediasorter.di.MediaCapabilitiesEntryPoint
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.settings.helpers.DefaultPlayerManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * S0133: Reconciles system component state (ACTION_SEND share-sheet aliases, ACTION_VIEW player aliases)
 * with the values currently persisted in DataStore.
 *
 * Invoked once per process start from FastMediaSorterApp.onCreate. The underlying
 * PackageManager.setComponentEnabledSetting is idempotent - calling it with the value already in effect
 * is a no-op at the OS level - so this bootstrap is safe to run on every start without any "first run"
 * flag. After a reinstall or clear-data, the system forgets runtime overrides; this bootstrap restores
 * them automatically on the next launch, so the app reappears in the OS share-sheet without user action.
 *
 * Flavor filtering is delegated entirely to DefaultPlayerManager - only aliases supported by the current
 * BuildConfig flags are ever touched.
 */
object DefaultPlayerStateBootstrapper {

    suspend fun apply(context: Context, settingsRepository: SettingsRepository) {
        val caps = EntryPointAccessors.fromApplication(
            context.applicationContext, MediaCapabilitiesEntryPoint::class.java
        ).mediaCapabilities()
        // S0477: flavors without a default player (e.g. lite) strip every Standalone alias and
        // MediaButtonRestartReceiver from their merged manifest. Toggling a component the package
        // does not declare makes setComponentEnabledSetting throw, which surfaced as a red ERROR
        // toast during onboarding. All other call sites already gate on this capability; the
        // bootstrapper must too.
        if (!caps.supportsDefaultPlayer) {
            return
        }
        val settings = settingsRepository.getSettings().first()
        DefaultPlayerManager.applyShareReceiverState(context, settings.acceptSharedFiles, caps)
        DefaultPlayerManager.applyPrimaryPlayerState(context, settings.isPrimaryMediaPlayer, caps)
    }
}
