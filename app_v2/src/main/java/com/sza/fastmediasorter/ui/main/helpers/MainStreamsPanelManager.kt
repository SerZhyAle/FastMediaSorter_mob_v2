package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.ViewMainStreamsPanelBinding
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamSourcesUseCase
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S0756: owns the main-window streams panel - a wide "Streams" entry button leading a horizontally
 * scrolling row of pinned channels. The pinned list comes from the existing pin source
 * ([ObservePinnedStreamSourcesUseCase]); thumbnails reuse the favicon sprite-atlas (S0668). Channel
 * taps route into [onPlayChannel] (the host launches Streams with the channel auto-played, so the
 * existing per-channel launch logic is reused, not duplicated).
 *
 * Visibility is owned by [MainActivity]; this manager only renders. The label rule (thumbnail only vs
 * thumbnail + short name) follows the available window width via R.bool.main_streams_panel_show_labels.
 */
class MainStreamsPanelManager(
    private val panel: ViewMainStreamsPanelBinding,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val observePinnedStreamSources: ObservePinnedStreamSourcesUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
    private val onOpenStreams: () -> Unit,
    private val onPlayChannel: (StreamSourceEntity) -> Unit,
    // S0770: per-item menu actions (new-window launches, remove/unpin, availability gate).
    private val menuActions: StreamsPanelMenuActions,
) {

    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    // url -> sprite-atlas tile index, loaded once off-Main; an empty map renders text-only chips.
    @Volatile
    private var faviconCoords: Map<String, Int> = emptyMap()

    private val adapter = StreamPanelChannelAdapter(
        onChannelClick = onPlayChannel,
        onChannelOverflow = { channel, anchor -> showChannelMenu(channel, anchor) },
        faviconResolver = { url -> faviconCoords[url] },
        faviconTileLoader = { index -> faviconSlicer.tileFor(index) },
        faviconScope = scope,
    )

    fun setup() {
        panel.rvStreamChannels.layoutManager =
            LinearLayoutManager(panel.root.context, LinearLayoutManager.HORIZONTAL, false)
        panel.rvStreamChannels.adapter = adapter
        panel.btnStreamsPanelEntry.setOnClickListenerDebounced { onOpenStreams() }
        // S0770: the Streams entry element also gets a menu (Open / Open-in-new-window; no Remove).
        panel.btnStreamsPanelEntry.setOnLongClickListener {
            showEntryMenu(panel.btnStreamsPanelEntry)
            true
        }
        panel.btnStreamsEntryMenu.setOnClickListener { showEntryMenu(panel.btnStreamsEntryMenu) }
        applyShowLabels()

        // Load the favicon coords off the main thread, then repaint any chips already shown.
        scope.launch {
            faviconCoords = faviconAtlasStore.coords()
            adapter.refreshFavicons()
        }

        lifecycleOwner.collectOnLifecycle(observePinnedStreamSources()) { sources ->
            Timber.d("S0756: streams panel pinned channels=${sources.size}")
            adapter.submitList(sources)
        }
    }

    /** MainActivity owns the show/hide decision (settings + flavor gate); this only paints it. */
    fun setVisible(visible: Boolean) {
        panel.root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Re-reads the width-driven label rule after a rotation / window resize. */
    fun onConfigurationChanged() {
        applyShowLabels()
    }

    private fun applyShowLabels() {
        adapter.setShowLabels(panel.root.resources.getBoolean(R.bool.main_streams_panel_show_labels))
    }

    /** S0770: per-channel menu - Open, optional Open-in-new-window, Remove (unpin). */
    private fun showChannelMenu(channel: StreamSourceEntity, anchor: View) {
        Timber.d("S0770: streams panel channel menu id=${channel.id}")
        val actions = mutableListOf<PanelItemContextMenu.Action>()
        actions += PanelItemContextMenu.Action(R.string.action_open) { onPlayChannel(channel) }
        if (menuActions.isNewWindowAvailable()) {
            actions += PanelItemContextMenu.Action(R.string.action_open_in_separate_window) {
                menuActions.onOpenChannelNewWindow(channel)
            }
        }
        actions += PanelItemContextMenu.Action(R.string.remove_action) { menuActions.onRemoveChannel(channel) }
        PanelItemContextMenu.show(anchor, actions)
    }

    /** S0770: Streams entry menu - Open, optional Open-in-new-window. No Remove (it is navigation). */
    private fun showEntryMenu(anchor: View) {
        Timber.d("S0770: streams panel entry menu")
        val actions = mutableListOf<PanelItemContextMenu.Action>()
        actions += PanelItemContextMenu.Action(R.string.action_open) { onOpenStreams() }
        if (menuActions.isNewWindowAvailable()) {
            actions += PanelItemContextMenu.Action(R.string.action_open_in_separate_window) {
                menuActions.onOpenStreamsNewWindow()
            }
        }
        PanelItemContextMenu.show(anchor, actions)
    }
}
