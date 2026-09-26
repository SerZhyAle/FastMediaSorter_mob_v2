package com.sza.fastmediasorter.wear.tile

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sza.fastmediasorter.wear.domain.model.WearTileContent
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.usecase.LoadWearTileContentUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
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
                val rootElement = layout.root
                if (rootElement == null) {
                    val error = IllegalStateException("Tile $kind has no root layout")
                    Timber.w(error, "Tile %s returned a layout with no root", kind)
                    future.setException(error)
                    return@launch
                }
                val timeline = TimelineBuilders.Timeline.fromLayoutElement(rootElement)
                val tile = TileBuilders.Tile.Builder()
                    .setResourcesVersion(resourcesVersionOf(content))
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

    /**
     * S2511: publishes an image for every glyph the current content draws.
     *
     * This used to answer an empty set under a constant version, which meant no tile could show an image at
     * all - a shortcut grid would have drawn empty buttons. The version is derived from the published ids
     * rather than fixed, because the renderer caches by that string: under a constant a changed icon set is
     * never re-fetched, and the tile keeps drawing the old glyphs.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val future = SettableFuture.create<ResourceBuilders.Resources>()
        serviceScope.launch {
            try {
                val drawableIds = drawableIdsOf(loadWearTileContentUseCase(kind))
                val builder = ResourceBuilders.Resources.Builder()
                    .setVersion(versionOf(drawableIds))
                drawableIds.forEach { drawableId ->
                    builder.addIdToImageMapping(
                        tileImageResourceId(drawableId),
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder()
                                    .setResourceId(drawableId)
                                    .build()
                            )
                            .build()
                    )
                }
                future.set(builder.build())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun resourcesVersionOf(content: WearTileContent): String = versionOf(drawableIdsOf(content))

    /** The glyphs [content] needs, in a stable order so the version does not change on re-ordering alone. */
    private fun drawableIdsOf(content: WearTileContent): List<Int> = when (content) {
        // S2511: the planned cells, not the raw entries. A grid that overflowed draws a cell no entry
        // carries, and publishing the entries instead would leave that cell's glyph unaddressable.
        // S3555: planned at the capacity the layout draws with, which shrinks while a program runs.
        is WearTileContent.Shortcuts ->
            planShortcutGrid(
                content.entries,
                overflow = overflowShortcut(this),
                capacity = shortcutGridCapacity(content)
            )
                .shown
                .map { tileShortcutIconFor(it.destinationId) }
                .distinct()
                .sorted()
        // The other states draw text only, which is why S2751 removed the always-null field they carried.
        is WearTileContent.Assigned,
        is WearTileContent.Unassigned,
        is WearTileContent.TargetMissing,
        WearTileContent.FavouritesEmpty -> emptyList()
    }

    private fun versionOf(drawableIds: List<Int>): String =
        if (drawableIds.isEmpty()) NO_IMAGES_VERSION else drawableIds.joinToString(separator = "-")

    override fun onDestroy() {
        // Releases an in-flight tile request that would otherwise retain this destroyed service.
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Distinct from any id-derived version, which always carries a digit. */
        private const val NO_IMAGES_VERSION = "none"
    }
}
