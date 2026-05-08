package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import javax.inject.Inject

class ApplyWearSettingsUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository
) {
    suspend operator fun invoke(payload: WearSettingsPayload) {
        preferencesRepository.setAudioEnabled(payload.audioEnabled)
        preferencesRepository.setVideoEnabled(payload.videoEnabled)
        preferencesRepository.setImagesEnabled(payload.imagesEnabled)
        preferencesRepository.setSlideshowEnabled(payload.slideshowEnabled)
        preferencesRepository.setSlideshowIntervalSeconds(payload.slideshowIntervalSeconds)
        preferencesRepository.setSlideshowWaitForFinish(payload.slideshowWaitForFinish)
        preferencesRepository.setDownloadAlbumArt(payload.downloadAlbumArt)
    }
}
