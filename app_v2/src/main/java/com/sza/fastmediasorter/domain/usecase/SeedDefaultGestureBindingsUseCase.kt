package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Seeds the three left-edge gesture direction bindings with sensible defaults on a fresh install:
 * up -> open the launch panel, right -> capture then open the editor, down -> silent capture.
 * No-op on flavors without the screen-gesture capability (empty controller set). It never turns the
 * overlay on (that stays an explicit user opt-in) and never overwrites an existing configuration -
 * the one-shot first-run guard lives in the caller.
 */
class SeedDefaultGestureBindingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>,
) {
    suspend operator fun invoke() {
        if (screenGestureControllers.isEmpty()) return
        Timber.d("S0662: seeding default gesture bindings (first run)")
        val current = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(
            current.copy(
                screenshotGestureActionUp = ScreenshotGestureAction.OPEN_PANEL,
                screenshotGestureActionRight = ScreenshotGestureAction.OPEN_IN_DRAW,
                screenshotGestureActionDown = ScreenshotGestureAction.SILENT_SCREENSHOT,
            )
        )
        Timber.i("Seeded default left-edge gesture bindings on first run")
    }
}
