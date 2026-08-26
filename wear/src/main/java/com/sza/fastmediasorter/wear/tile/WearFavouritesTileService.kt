package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1955: Wear OS TileService for displaying assigned Favourites tile.
 */
@AndroidEntryPoint
class WearFavouritesTileService : BaseWearTileService() {
    override val kind: WearTileKind = WearTileKind.FAVOURITES
}
