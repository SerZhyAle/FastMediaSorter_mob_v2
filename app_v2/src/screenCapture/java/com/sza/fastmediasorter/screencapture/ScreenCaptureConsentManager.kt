package com.sza.fastmediasorter.screencapture

import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1329: owns the disclosure flag that [ScreenCaptureConsentActivity] used to read and write through
 * an injected repository. Persisting a consent decision is business logic, not view work, so it moves
 * out of the host entirely (CLAUDE.md Rule 3).
 *
 * Lives in the `screenCapture` source set because its only caller does - the flavor that mounts it is
 * the only one that ships the capture consent flow at all.
 */
class ScreenCaptureConsentManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend fun isDisclosureAccepted(): Boolean =
        settingsRepository.getSettings().first().screenCaptureDisclosureAccepted

    suspend fun markDisclosureAccepted() {
        val current = settingsRepository.getSettings().first()
        settingsRepository.updateSettings(current.copy(screenCaptureDisclosureAccepted = true))
    }
}
