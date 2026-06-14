package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Force-enables every immediately-toggleable function setting plus the adjacent opt-ins, gated by the
 * flavor capability flags (S0409). Deliverable-gated features (OCR/translation) are intentionally NOT
 * touched here - they are downloaded and enabled-on-install by the welcome orchestrator, preserving the
 * "enable only after install" invariant.
 */
class ApplyEnableAllSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
) {
    /**
     * Read-modify-write of the latest snapshot so a concurrent profile/preset write is not clobbered.
     * The capability gate implements the "silently skip unavailable" decision: a feature absent from
     * this build/flavor keeps its current value instead of being force-enabled.
     */
    suspend operator fun invoke() {
        val current = settingsRepository.getSettings().first()
        val updated = current.copy(
            allFiles = true,
            supportAudio = if (mediaCapabilities.supportsAudio) true else current.supportAudio,
            supportVideos = if (mediaCapabilities.supportsVideo) true else current.supportVideos,
            supportText = if (mediaCapabilities.supportsDocuments) true else current.supportText,
            supportPdf = if (mediaCapabilities.supportsDocuments) true else current.supportPdf,
            supportOfficeDocuments = if (mediaCapabilities.supportsDocuments) true else current.supportOfficeDocuments,
            supportEpub = if (mediaCapabilities.supportsEpub) true else current.supportEpub,
            enablePersistentAudioPlayback = if (mediaCapabilities.supportsAudio) true else current.enablePersistentAudioPlayback,
            acceptSharedFiles = true,
            isPrimaryMediaPlayer = true,
        )
        settingsRepository.updateSettings(updated)
        Timber.i(
            "ApplyEnableAllSettingsUseCase: enable-all applied (audio=%b video=%b docs=%b)",
            mediaCapabilities.supportsAudio,
            mediaCapabilities.supportsVideo,
            mediaCapabilities.supportsDocuments,
        )
    }
}
