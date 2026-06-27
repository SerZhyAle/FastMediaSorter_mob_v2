package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

        // S0727: read the full settings snapshot once off-Main, then pass strip visibility into
        // setEnabled so the controller does no settings IO on Main.immediate below.
        val settings = settingsRepository.get().getSettings().first()
        if (!settings.gestureOverlayEnabled) return
        val stripVisible = settings.screenshotGestureStripVisible

        withContext(Dispatchers.Main.immediate) {
            controllers.forEach { controller ->
                if (!controller.isOverlayPermissionGranted(appContext)) {
                    Timber.w(
                        "ScreenGestureOverlayStartupCoordinator: restore skipped - permission missing"
                    )
                    return@forEach
                }
                controller.setEnabled(true, stripVisible)
                Timber.i(
                    "ScreenGestureOverlayStartupCoordinator: overlay restore requested on startup"
                )
            }
        }
    }
}
