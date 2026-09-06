package com.sza.fastmediasorter.domain.usecase.mirror

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Writes the mirror's three remembered values back into the shared settings (strategic S1924 2.6), so a
 * chosen zoom, flip and backlight state survive a restart.
 *
 * One use case per value would be three classes over one field each; they are grouped because they are
 * the same decision - "remember how the owner left the mirror" - and are always wired together.
 */
class UpdateMirrorSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend fun setZoomRatio(ratio: Float) {
        settingsRepository.updateSettings { it.copy(mirrorZoomRatio = ratio) }
    }

    suspend fun setHorizontallyFlipped(flipped: Boolean) {
        settingsRepository.updateSettings { it.copy(mirrorHorizontallyFlipped = flipped) }
    }

    suspend fun setBacklightOn(on: Boolean) {
        settingsRepository.updateSettings { it.copy(mirrorBacklightOn = on) }
    }
}
