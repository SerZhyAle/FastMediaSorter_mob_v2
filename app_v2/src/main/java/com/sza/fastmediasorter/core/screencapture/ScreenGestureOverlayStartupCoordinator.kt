package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Restores the screenshot gesture overlay when the process enters foreground after a cold start.
 * The setting is persisted, but the fallback overlay host on API 26..29 must be started again
 * after process death.
 */
class ScreenGestureOverlayStartupCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: Lazy<com.sza.fastmediasorter.domain.repository.SettingsRepository>,
    private val controllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>
) {

    suspend fun restoreIfNeeded() {
        if (controllers.isEmpty()) return

        val gestureOverlayEnabled = settingsRepository.get().getSettings()
            .map { it.gestureOverlayEnabled }
            .first()
        if (!gestureOverlayEnabled) return

        withContext(Dispatchers.Main.immediate) {
            controllers.forEach { controller ->
                if (!controller.isOverlayPermissionGranted(appContext)) {
                    Timber.w(
                        "ScreenGestureOverlayStartupCoordinator: restore skipped - permission missing"
                    )
                    return@forEach
                }
                controller.setEnabled(true)
                Timber.i(
                    "ScreenGestureOverlayStartupCoordinator: overlay restore requested on startup"
                )
            }
        }
    }
}
