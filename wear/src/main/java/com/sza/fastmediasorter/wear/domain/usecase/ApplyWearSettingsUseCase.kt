package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.core.util.WearLocaleManager
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class ApplyWearSettingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) {
    suspend operator fun invoke(payload: WearSettingsPayload) {
        preferencesRepository.setAudioEnabled(payload.audioEnabled)
        preferencesRepository.setVideoEnabled(payload.videoEnabled)
        preferencesRepository.setImagesEnabled(payload.imagesEnabled)
        preferencesRepository.setSlideshowEnabled(payload.slideshowEnabled)
        preferencesRepository.setSlideshowIntervalSeconds(payload.slideshowIntervalSeconds)
        preferencesRepository.setDownloadAlbumArt(payload.downloadAlbumArt)
        // S1781: absent means "the phone did not send this", never "reset it" - an older phone must
        // not silently undo a view mode or keep-awake choice made on the watch.
        payload.viewMode?.let { preferencesRepository.setViewMode(WearViewMode.fromNameOrDefault(it)) }
        payload.keepScreenAwakeOutsidePlayers?.let {
            preferencesRepository.setKeepScreenAwakeOutsidePlayers(it)
        }
        payload.fileListViewMode?.let {
            preferencesRepository.setFileListViewMode(WearViewMode.fromNameOrDefault(it))
        }
        payload.backgroundMode?.let {
            Timber.d("S2000: watch received background mode=$it from the phone")
            preferencesRepository.setBackgroundMode(WearBackgroundMode.fromNameOrDefault(it))
        }
        // S1814: apply language received from phone companion if supported by the watch.
        payload.appLanguage?.let { rawLanguage ->
            WearLocaleManager.resolveSupportedTag(context, rawLanguage)?.let { resolvedTag ->
                preferencesRepository.setAppLanguage(resolvedTag)
                WearLocaleManager.applyLocale(context, resolvedTag)
            }
        }
    }
}
