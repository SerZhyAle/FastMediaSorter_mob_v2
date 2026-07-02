package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * S0770: per-item menu actions for the main-window streams panel, grouped so [MainStreamsPanelManager]
 * keeps a lean constructor. The host (MainActivity) owns the launch/settings work behind each callback.
 */
class StreamsPanelMenuActions(
    /** Open the Streams section in a separate window. */
    val onOpenStreamsNewWindow: () -> Unit,
    /** Play the given channel in a separate window. */
    val onOpenChannelNewWindow: (StreamSourceEntity) -> Unit,
    /** Remove the channel from the panel (drop its pin), after host confirmation. */
    val onRemoveChannel: (StreamSourceEntity) -> Unit,
    /** S0779: turn off the Streams master toggle from the entry menu; the panel hides on settings refresh. */
    val onDisableStreams: () -> Unit,
    /** S0782: hide just the main-window streams panel (keep Streams enabled); the entry button moves back to
     *  the programs panel/menu. Distinct from [onDisableStreams], which turns the whole feature off. */
    val onHideStreamsPanel: () -> Unit,
    /** S0780: open Settings on the Streams group (deep-link from the entry menu's "Configure"). */
    val onConfigureStreams: () -> Unit,
    /** Whether "Open in new window" should be offered (multi-window available). */
    val isNewWindowAvailable: () -> Boolean,
    /** S0783: add/remove the channel from the shared Favorites (independent of the pin). */
    val onToggleFavorite: (StreamSourceEntity) -> Unit,
    /** S0783: whether the Favorites feature is enabled (gates the per-channel favorite item). */
    val isFavoritesEnabled: () -> Boolean,
    /** S0783: whether the channel is currently favorited (drives the add vs remove label). */
    val isChannelFavorite: (StreamSourceEntity) -> Boolean,
)
