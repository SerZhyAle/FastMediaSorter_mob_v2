package com.sza.fastmediasorter.screencapture

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S0774/S1195: owns the persisted "continuous recording disclosure accepted" flag for
 * [ScreenVideoRecordingConsentActivity], so the consent host reads and writes no settings itself
 * (CLAUDE.md Rule 3).
 */
class ScreenRecordingDisclosureManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    /** True once the user has accepted the continuous-recording disclosure. */
    suspend fun isAccepted(): Boolean = settingsRepository.getSettings().first().screenRecordingDisclosureAccepted

    /** Records the acceptance so later recordings skip straight to the platform consent. */
    suspend fun accept() {
        val current = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(current.copy(screenRecordingDisclosureAccepted = true))
    }
}
