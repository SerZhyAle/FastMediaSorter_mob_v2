package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearSettingsRegistry
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * S2093: reads the watch's whole settings state out as one payload.
 *
 * The counterpart the watch never had - [ApplyWearSettingsUseCase] could write a whole set in, and
 * nothing could read one out, which is why the phone could only ever show its memory of its own last
 * send instead of what the watch actually holds.
 */
class GatherWearSettingsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: WearPreferencesRepository
) {

    suspend operator fun invoke(): WearSettingsPayload {
        Timber.d("S2461: gathering watch settings, reporting version=${BuildConfig.VERSION_NAME}")
        return gather()
    }

    private suspend fun gather(): WearSettingsPayload = WearSettingsPayload(
        audioEnabled = preferencesRepository.isAudioEnabled.first(),
        videoEnabled = preferencesRepository.isVideoEnabled.first(),
        imagesEnabled = preferencesRepository.isImagesEnabled.first(),
        documentsEnabled = preferencesRepository.isDocumentsEnabled.first(),
        slideshowEnabled = preferencesRepository.isSlideshowEnabled.first(),
        slideshowIntervalSeconds = preferencesRepository.slideshowIntervalSeconds.first(),
        downloadAlbumArt = preferencesRepository.downloadAlbumArt.first(),
        viewMode = preferencesRepository.viewMode.first().name,
        keepScreenAwakeOutsidePlayers = preferencesRepository.keepScreenAwakeOutsidePlayers.first(),
        fileListViewMode = preferencesRepository.fileListViewMode.first().name,
        // appLanguage is a PHONE_ONLY registry entry: the watch inherits it and never holds the later
        // value, so reporting it back could only overwrite the phone's own choice with an echo.
        appLanguage = null,
        backgroundMode = preferencesRepository.backgroundMode.first().name,
        streamsSectionEnabled = preferencesRepository.streamsSectionEnabled.first(),
        disableAnimations = preferencesRepository.isAnimationsDisabled.first(),
        backgroundPlaybackEnabled = preferencesRepository.backgroundPlaybackEnabled.first(),
        fieldTimestamps = preferencesRepository.settingTimestamps.first(),
        capabilities = mapOf(
            WearSettingsRegistry.CAPABILITY_AUTO_ROTATION_SENSOR to hasAutoRotationSensor()
        ),
        // S2461: the same string the watch's own settings screen shows, so the phone displays the
        // version the owner would read on the watch itself rather than a second, differently-derived one.
        appVersionName = BuildConfig.VERSION_NAME
    )

    // The same check the auto-rotation row uses to decide whether it exists at all, so the phone can
    // grey out what this watch cannot do instead of offering a switch that changes nothing.
    private fun hasAutoRotationSensor(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
}
