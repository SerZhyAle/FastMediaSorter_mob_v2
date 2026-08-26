package com.sza.fastmediasorter.wear.tile

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearTileContentUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S1955: Base TileService sharing layout building and content loading logic across Wear OS tiles.
 */
@AndroidEntryPoint
abstract class BaseWearTileService : TileService() {

    @Inject
    lateinit var loadWearTileContentUseCase: LoadWearTileContentUseCase

    @Inject
    lateinit var tileLayoutBuilder: WearTileLayoutBuilder

    protected abstract val kind: WearTileKind

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Suppress("TooGenericExceptionCaught")
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val future = SettableFuture.create<TileBuilders.Tile>()
        serviceScope.launch {
            try {
                val content = loadWearTileContentUseCase(kind)
                val layout = tileLayoutBuilder.build(content, requestParams.deviceConfiguration)
                val rootElement = layout.root ?: return@launch
                val timeline = TimelineBuilders.Timeline.fromLayoutElement(rootElement)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(timeline)
                    .build()
                future.set(tile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
