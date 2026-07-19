package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.databinding.ViewMainStreamsPanelBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamSourcesUseCase
import com.sza.fastmediasorter.ui.streams.FaviconAtlasSlicer
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
/**
 * S0756: owns the main-window streams panel - a wide "Streams" entry button leading a horizontally
 * scrolling row of pinned channels. The pinned list comes from the existing pin source
 * ([ObservePinnedStreamSourcesUseCase]); thumbnails reuse the favicon sprite-atlas (S0668). S0777: a
 * tapped AUDIO channel plays inline via [MainStreamsInlineAudioManager] (bottom now-playing control in
 * the home window); a VIDEO/RTSP channel defers to the Streams screen via the host's onPlayVideo.
 *
 * Visibility is owned by [MainActivity]; this manager only renders. S0779: the label rule (icon-only vs
 * icon + short name, for both the entry button and the channel chips) follows orientation via
 * R.bool.main_streams_panel_show_labels - landscape shows labels, portrait stays compact.
 */
@UnstableApi
@Suppress("LongParameterList") // View-manager: its views + per-item callbacks + the shared-row collapse chip (S0809).
class MainStreamsPanelManager(
    private val panel: ViewMainStreamsPanelBinding,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope,
    private val observePinnedStreamSources: ObservePinnedStreamSourcesUseCase,
    private val faviconAtlasStore: FaviconAtlasStore,
    private val onOpenStreams: () -> Unit,
    // S0777: plays an AUDIO channel inline in the home window; a VIDEO/RTSP tap defers to the Streams screen.
    private val inlineAudio: MainStreamsInlineAudioManager,
    // S0770: per-item menu actions (new-window launches, remove/unpin, availability gate).
    private val menuActions: StreamsPanelMenuActions,
    // S0808: persist the collapsed-strip state, mirroring the S0807 programs-panel collapse.
    private val settingsRepository: SettingsRepository,
    // S0809: collapsed representation is a chip in the shared collapsed-panels row, not an in-panel strip.
    private val collapsedChip: View,
) {

    // S0808: availability (owned by MainActivity) + collapsed (owned here) drive the content<->strip swap.
    private var available = false
    private var collapsed = false

    // S1061: third axis - an on-but-unpinned panel shows the hint instead of the empty channel row.
    // Starts true so the first paint (before the pin flow emits) is the hint, not a blank slot.
    private var pinnedEmpty = true

    private val faviconSlicer = FaviconAtlasSlicer { faviconAtlasStore.atlasFile() }

    // url -> sprite-atlas tile index, loaded once off-Main; an empty map renders text-only chips.
    @Volatile
    private var faviconCoords: Map<String, Int> = emptyMap()

    private val adapter = StreamPanelChannelAdapter(
        onChannelClick = ::playChannel,
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
        // S0779: the entry's long-press menu (Open / Open-in-new-window / Disable) replaced the separate
        // three-dots button, so the entry stays compact in portrait.
        panel.btnStreamsPanelEntry.setOnLongClickListener {
            showEntryMenu(panel.btnStreamsPanelEntry)
            true
        }
        applyShowLabels()

        // S0808: tap the collapsed strip to expand; load the persisted collapsed state and apply it.
        collapsedChip.setOnClickListener { setCollapsed(false) }
        scope.launch {
            collapsed = settingsRepository.getSettings().first().streamsPanelCollapsed
            applyVisibility()
        }

        // Load the favicon coords off the main thread, then repaint any chips already shown.
        scope.launch {
            faviconCoords = faviconAtlasStore.coords()
            adapter.refreshFavicons()
        }

        lifecycleOwner.collectOnLifecycle(observePinnedStreamSources()) { sources ->
            adapter.submitList(sources)
            // S1061: same emission feeds the list and the hint, so the two can never disagree.
            pinnedEmpty = sources.isEmpty()
            Timber.d("S1061: pinned streams=${sources.size} -> empty hint=${sources.isEmpty()}")
            applyVisibility()
        }
    }

    /** MainActivity owns the show/hide decision (settings + flavor gate); this only paints it. */
    fun setVisible(visible: Boolean) {
        available = visible
        applyVisibility()
    }

    /**
     * S0808: root shows when available; inside, the content row and the collapsed strip are mutually
     * exclusive (mirror of the programs panel). MainActivity still owns the root's availability.
     * S1061: within a shown content row, the channel list and the empty hint share one weighted slot and
     * swap on the pinned count; feeding [resolveContentSlot] the resolved row visibility is what keeps a
     * collapsed or unavailable panel from painting the hint.
     */
    private fun applyVisibility() {
        val (contentVisible, stripVisible) = resolveVisibility(available, collapsed)
        panel.root.isVisible = available
        panel.streamsPanelContent.isVisible = contentVisible
        collapsedChip.isVisible = stripVisible
        val (channelsVisible, hintVisible) = resolveContentSlot(contentVisible, pinnedEmpty)
        panel.rvStreamChannels.isVisible = channelsVisible
        panel.tvStreamsPanelEmptyHint.isVisible = hintVisible
    }

    /** S0808: entry menu "Collapse panel" (true) and strip tap (false); persists + re-applies visibility. */
    private fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        // A GONE view loses focus, so hand it over before swapping the row for the strip (or back).
        val contentHadFocus = panel.streamsPanelContent.hasFocus()
        val stripHadFocus = collapsedChip.hasFocus()
        collapsed = value
        applyVisibility()
        if (value && contentHadFocus) {
            collapsedChip.requestFocus()
        } else if (!value && stripHadFocus) {
            panel.btnStreamsPanelEntry.requestFocus()
        }
        scope.launch {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(current.copy(streamsPanelCollapsed = value))
        }
    }

    /** Re-reads the orientation-driven label rule after a rotation / window resize. */
    fun onConfigurationChanged() {
        applyShowLabels()
    }

    /**
     * S0779: the orientation-driven label rule (landscape = labels, portrait = icons) covers both the
     * channel chips and the entry button. In portrait the entry collapses to icon-only.
     * S1037: in portrait the entry occupies the shared top-panel leading zone so the first channel
     * starts at the same X as the programs items and resource tabs; landscape keeps its label width.
     * S1068: in portrait that leading zone is the accented first cell (60dp = base module +12dp), aligning
     * the streams entry with the command bar Exit, programs three-dots and first tab; landscape unchanged.
     */
    private fun applyShowLabels() {
        val resources = panel.root.resources
        val showLabels = resources.getBoolean(R.bool.main_streams_panel_show_labels)
        adapter.setShowLabels(showLabels)
        val entry = panel.btnStreamsPanelEntry
        entry.text = if (showLabels) entry.context.getString(R.string.streams_title) else ""
        entry.minWidth = resources.getDimensionPixelSize(
            if (showLabels) R.dimen.main_streams_entry_min_width else R.dimen.main_panel_first_item_width
        )
    }

    /** S0777: a channel tap routes through the inline-audio coordinator (AUDIO inline, VIDEO/RTSP deferred). */
    private fun playChannel(channel: StreamSourceEntity) = inlineAudio.playChannel(channel)

    /** S0770: per-channel menu - Open, optional Open-in-new-window, Remove (unpin). */
    private fun showChannelMenu(channel: StreamSourceEntity, anchor: View) {
        val actions = mutableListOf<PanelItemContextMenu.Action>()
        actions += PanelItemContextMenu.Action(R.string.action_open) { playChannel(channel) }
        if (menuActions.isNewWindowAvailable()) {
            actions += PanelItemContextMenu.Action(R.string.action_open_in_separate_window) {
                menuActions.onOpenChannelNewWindow(channel)
            }
        }
        // S0783: add/remove from Favorites when the feature is on; label reflects the current state.
        if (menuActions.isFavoritesEnabled()) {
            val favLabel = if (menuActions.isChannelFavorite(channel)) {
                R.string.streams_remove_from_favorites
            } else {
                R.string.streams_add_to_favorites
            }
            actions += PanelItemContextMenu.Action(favLabel) { menuActions.onToggleFavorite(channel) }
        }
        actions += PanelItemContextMenu.Action(R.string.remove_action) { menuActions.onRemoveChannel(channel) }
        // S0810: header shows the channel's name so the icon-only chip is understandable.
        PanelItemContextMenu.show(anchor, actions, title = channel.title)
    }

    /**
     * S0779/S0782: Streams entry menu - Open, optional Open-in-new-window, Configure, Hide panel (S0782:
     * hides only this panel, Streams stays on and the entry returns to the programs panel/menu), Disable
     * (turns off the Streams master toggle entirely). No Remove: the entry is navigation, not a pinned channel.
     */
    private fun showEntryMenu(anchor: View) {
        val actions = mutableListOf<PanelItemContextMenu.Action>()
        actions += PanelItemContextMenu.Action(R.string.action_open) { onOpenStreams() }
        if (menuActions.isNewWindowAvailable()) {
            actions += PanelItemContextMenu.Action(R.string.action_open_in_separate_window) {
                menuActions.onOpenStreamsNewWindow()
            }
        }
        // S0780: jump straight to the Streams settings group.
        actions += PanelItemContextMenu.Action(R.string.action_configure) {
            menuActions.onConfigureStreams()
        }
        // S0808: collapse the panel into its labelled strip (mirror of the programs panel's Collapse).
        actions += PanelItemContextMenu.Action(R.string.streams_panel_collapse_action) {
            setCollapsed(true)
        }
        // S0782: hide only this panel (Streams stays enabled, entry returns to the programs panel/menu).
        actions += PanelItemContextMenu.Action(R.string.streams_panel_hide_action) {
            menuActions.onHideStreamsPanel()
        }
        actions += PanelItemContextMenu.Action(R.string.streams_panel_disable_action) {
            menuActions.onDisableStreams()
        }
        // S0810: header names the entry ("Streams") so the icon-only button is understandable.
        PanelItemContextMenu.show(anchor, actions, title = anchor.context.getString(R.string.streams_title))
    }

    companion object {
        /**
         * S0808: (contentVisible, stripVisible) for the given availability + collapsed state. The panel
         * root owns availability; when hidden both are false. The content row and strip are never both shown.
         */
        internal fun resolveVisibility(available: Boolean, collapsed: Boolean): Pair<Boolean, Boolean> =
            (available && !collapsed) to (available && collapsed)

        /**
         * S1061: (channelsVisible, hintVisible) for the weighted slot shared by the channel list and the
         * empty hint. [contentVisible] is [resolveVisibility]'s own verdict, so a hidden or collapsed
         * panel paints neither; inside a shown row exactly one of the two is on.
         */
        internal fun resolveContentSlot(contentVisible: Boolean, pinnedEmpty: Boolean): Pair<Boolean, Boolean> =
            (contentVisible && !pinnedEmpty) to (contentVisible && pinnedEmpty)
    }
}
