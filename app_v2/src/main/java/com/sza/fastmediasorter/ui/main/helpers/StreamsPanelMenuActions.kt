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
    /** Whether "Open in new window" should be offered (multi-window available). */
    val isNewWindowAvailable: () -> Boolean,
)
