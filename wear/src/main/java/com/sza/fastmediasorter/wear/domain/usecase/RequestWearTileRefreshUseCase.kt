package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import androidx.wear.tiles.TileService
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.tile.WearFavouritesTileService
import com.sza.fastmediasorter.wear.tile.WearResourceTileService
import com.sza.fastmediasorter.wear.tile.WearStreamTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S1955: requests the Wear OS system to redraw a tile when its content changes.
 *
 * Tile updates are event-driven only - no freshness interval is configured.
 */
class RequestWearTileRefreshUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(kind: WearTileKind) {
        val serviceClass: Class<out TileService> = when (kind) {
            WearTileKind.RESOURCE -> WearResourceTileService::class.java
            WearTileKind.STREAM -> WearStreamTileService::class.java
            WearTileKind.FAVOURITES -> WearFavouritesTileService::class.java
        }
        TileService.getUpdater(context).requestUpdate(serviceClass)
    }
}
