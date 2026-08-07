package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Seeds the LEFT_TOP edge-band gesture direction bindings with sensible defaults on a fresh install:
 * up -> open the launch panel, right -> capture then open the editor, down -> silent capture. The other
 * three bands (S0847) stay disabled with DO_NOT_USE slots (opt-in). No-op on flavors without the
 * screen-gesture capability (empty controller set). It never turns the overlay on (that stays an explicit
 * user opt-in) and never overwrites an existing configuration - the one-shot first-run guard lives in the caller.
 */
class SeedDefaultGestureBindingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>,
) {
    suspend operator fun invoke() {
        if (screenGestureControllers.isEmpty()) return
        val current = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(
            current.copy(
                screenshotGesture = current.screenshotGesture.copy(
                    leftTopUp = ScreenshotGestureAction.OPEN_PANEL,
                    leftTopRight = ScreenshotGestureAction.OPEN_IN_DRAW,
                    leftTopDown = ScreenshotGestureAction.SILENT_SCREENSHOT,
                )
            )
        )
        Timber.i("Seeded default LEFT_TOP edge-band gesture bindings on first run")
    }
}
