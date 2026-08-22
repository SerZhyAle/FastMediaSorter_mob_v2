package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.GadgetLauncherListBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamSourcesUseCase
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S0404: the stream catalog on the desktop - pinned channels first, then the rest of the user's order.
 */
class StreamsGadget @Inject constructor(
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_STREAMS
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 2
    override val labelRes: Int = R.string.launcher_gadget_streams
    override val iconRes: Int = R.drawable.ic_cast
    override val requiresResourceParam: Boolean = false

    /**
     * Held here, NOT in the view. The slicer's whole point is decoding the atlas once and cropping
     * tiles off the cached bitmap - and the shipped atlas is 512x3296 RGBA, i.e. ~6.4 MB decoded to
     * serve ten 32px tiles. A view-scoped slicer would re-decode all of it every time the desktop
     * rebuilt its cells. This gadget is constructed once (the registry is `@Singleton`), so the atlas
     * is decoded once per process - matching how `StreamsActivity` and `MainStreamsPanelManager` scope
     * theirs to their host rather than to a row.
     */
    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        StreamsGadgetView(container.context, host, observeStreams, faviconAtlasStore, faviconSlicer)
}

private class StreamsGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    private val observeStreams: ObserveStreamSourcesUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
    private val faviconSlicer: FaviconAtlasSlicer,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherListBinding.inflate(LayoutInflater.from(context), this)

    /**
     * S1832: row id -> channel identity, because the two cannot be the same field.
     *
     * The row id has to stay unique or the list's DiffUtil key stops distinguishing rows, and an
     * identity is deliberately not unique - the published bank folds several addresses of one channel
     * onto one key. The command needs the identity: [ExecuteLauncherCommandUseCase] records the encoded
     * command in the launch journal and counts it in the "most used" tally, so launching by row id here
     * would file the same channel under a second key every time the catalog reissued its id.
     */
    private val identityByRowId = mutableMapOf<String, String>()

    private val rowAdapter = LauncherGadgetRowAdapter { row ->
        host.run(LauncherCellCommand.Stream(identityByRowId[row.id] ?: row.id))
    }

    init {
        binding.gadgetListTitle.setText(R.string.launcher_gadget_streams)
        binding.gadgetListItems.layoutManager = LinearLayoutManager(context)
        binding.gadgetListItems.adapter = rowAdapter
        binding.gadgetListMessage.setOnClickListener {
            host.run(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_STREAMS))
        }
    }

    /**
     * Collects rather than snapshotting: inside [LauncherGadgetView.onActive] the collection is already
     * scoped to this view AND to STARTED, so staying subscribed costs nothing and the list cannot go
     * stale behind a catalog change that lands while the desktop is up.
     */
    override suspend fun CoroutineScope.onActive() {
        // Favicons live in a packed atlas beside the catalog, not on the network: coords maps a channel
        // url to a tile index, the slicer crops that tile from one decoded PNG. Nothing here goes online.
        val coords = runCatching { faviconAtlasStore.coords() }.getOrDefault(emptyMap())
        // The catalog is ordered in SQL (pinned DESC, sortIndex ASC, addedAt DESC), so the gadget must
        // not re-sort - it would silently disagree with the Streams screen.
        observeStreams().collect { all ->
            val sources = all.take(CHANNEL_LIMIT)
            binding.gadgetListMessage.isVisible = sources.isEmpty()
            binding.gadgetListItems.isVisible = sources.isNotEmpty()
            if (sources.isEmpty()) {
                binding.gadgetListMessage.setText(R.string.launcher_gadget_streams_empty)
                binding.gadgetListMessage.isClickable = true
                binding.gadgetListMessage.isFocusable = true
                return@collect
            }
            identityByRowId.clear()
            sources.forEach { identityByRowId[it.id] = it.identityKey }
            rowAdapter.submitList(
                sources.map { source ->
                    val tile = coords[source.url]?.let { faviconSlicer.tileFor(it) }
                    LauncherGadgetRow(
                        id = source.id,
                        title = source.title,
                        iconRes = R.drawable.ic_cast.takeIf { tile == null },
                        bitmap = tile,
                        // ic_cast fills white and would vanish on the card; a favicon must not be tinted.
                        tintIcon = tile == null,
                    )
                }
            )
        }
    }

    private companion object {
        /**
         * How many channels are LOADED, not how many are visible: a 2x2 cell fits about six rows at
         * the default density and three at factor 1.5, and the rest are a scroll away.
         */
        const val CHANNEL_LIMIT = 10
    }
}
