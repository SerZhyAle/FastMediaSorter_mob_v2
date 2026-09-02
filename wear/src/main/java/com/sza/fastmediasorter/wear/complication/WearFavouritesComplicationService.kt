package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2047: Wear OS complication data source for the favourites count.
 */
@AndroidEntryPoint
class WearFavouritesComplicationService : BaseWearComplicationService() {
    override val kind: WearComplicationKind = WearComplicationKind.FAVOURITES_COUNT
}
