package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.provider.Settings
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ScreenGestureOverlayControllerImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: Lazy<SettingsRepository>
) : ScreenGestureOverlayController {

    private val appContext = context.applicationContext

    override fun isOverlayPermissionGranted(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            OverlayHostService.stop(appContext)
            return
        }

        if (Settings.canDrawOverlays(appContext)) {
            OverlayHostService.start(appContext)
        } else {
            OverlayHostService.stop(appContext)
        }
    }

    override fun isEnabled(): Boolean = runBlocking {
        settingsRepository.get().getSettings().first().gestureOverlayEnabled
    }
}
