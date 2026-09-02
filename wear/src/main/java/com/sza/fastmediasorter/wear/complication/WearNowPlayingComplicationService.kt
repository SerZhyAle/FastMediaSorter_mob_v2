package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2047: Wear OS complication data source for the now playing / last played track.
 */
@AndroidEntryPoint
class WearNowPlayingComplicationService : BaseWearComplicationService() {
    override val kind: WearComplicationKind = WearComplicationKind.NOW_PLAYING
}
