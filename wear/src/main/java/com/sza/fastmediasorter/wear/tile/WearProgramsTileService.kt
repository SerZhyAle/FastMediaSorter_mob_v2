package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import dagger.hilt.android.AndroidEntryPoint

/** S2511: Tile Service for the grid of the watch's mini-programs. */
@AndroidEntryPoint
class WearProgramsTileService : BaseWearTileService() {
    override val kind: WearTileKind = WearTileKind.PROGRAMS
}
