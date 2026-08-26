package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import dagger.hilt.android.AndroidEntryPoint

/**
 * S1955: Wear OS TileService for displaying assigned Network Resource tile.
 *
 * Uses `Wear` prefix to avoid confusion with phone module's Quick Settings `TileService`.
 */
@AndroidEntryPoint
class WearResourceTileService : BaseWearTileService() {
    override val kind: WearTileKind = WearTileKind.RESOURCE
}
