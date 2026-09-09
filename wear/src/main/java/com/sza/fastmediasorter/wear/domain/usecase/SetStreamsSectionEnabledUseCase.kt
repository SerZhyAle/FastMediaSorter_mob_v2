package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * S2511: switches the Streams section on or off, and tells the system the sections tile has changed.
 *
 * Writing the preference alone left the tile stale until the owner removed and re-added it: tile content is
 * pulled by the system, no freshness interval is configured for this family, and nothing else on the watch
 * asks for a redraw. The device pass on 2026-09-09 found the tile still offering Streams after the switch
 * had been off for minutes.
 *
 * The two halves are bound in one use case rather than repeated at each call site, because the setting has
 * two writers - the watch's own settings screen and the settings set the phone pushes - and a third one
 * added later would be free to forget the second half.
 */
class SetStreamsSectionEnabledUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val requestWearTileRefresh: RequestWearTileRefreshUseCase
) {

    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setStreamsSectionEnabled(enabled)
        Timber.d("S2511: streams section switched to %s, asking the sections tile to redraw", enabled)
        requestWearTileRefresh(WearTileKind.SECTIONS)
    }
}
