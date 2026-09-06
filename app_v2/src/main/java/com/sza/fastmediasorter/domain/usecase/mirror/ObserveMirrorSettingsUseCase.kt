package com.sza.fastmediasorter.domain.usecase.mirror

import com.sza.fastmediasorter.domain.model.MirrorSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Reads the mirror's three remembered values out of the shared settings (strategic S1924 2.6).
 *
 * Exists so the screen reaches the settings through a use case instead of injecting the repository into
 * its ViewModel (S2103). [distinctUntilChanged] keeps an unrelated settings write - any of the other
 * couple of hundred fields - from re-emitting here and re-applying the zoom or re-raising the window
 * brightness for nothing.
 */
class ObserveMirrorSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    operator fun invoke(): Flow<MirrorSettings> = settingsRepository.getSettings()
        .map {
            MirrorSettings(
                zoomRatio = it.mirrorZoomRatio,
                horizontallyFlipped = it.mirrorHorizontallyFlipped,
                backlightOn = it.mirrorBacklightOn,
            )
        }
        .distinctUntilChanged()
}
