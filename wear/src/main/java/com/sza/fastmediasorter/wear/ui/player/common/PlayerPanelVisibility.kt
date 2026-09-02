package com.sza.fastmediasorter.wear.ui.player.common

import kotlinx.coroutines.delay

/**
 * How long a player's controls stay on screen after they are revealed.
 *
 * The value carries over from the video player, which held it as a bare `3000` inside its own state
 * holder. Naming it here is what lets a second screen obey the same rule instead of picking its own
 * number, which is how the three incompatible behaviours in S2006 section 1 came about.
 */
internal const val PLAYER_PANEL_HIDE_DELAY_MS = 3_000L

/**
 * Waits out the panel delay, and answers whether the caller should now hide its controls.
 *
 * "Active" is each screen's own idea of playing: sound or frames for the audio and video players, a
 * running slideshow for the image viewer. Inactive means the countdown never starts - a paused video
 * keeps its transport, and a picture the user is paging by hand keeps its panel.
 *
 * @return true when the delay elapsed and the panel should go; false when there was nothing to wait
 * for. A caller that is cancelled mid-delay never sees either, which is the intended way to abort a
 * scheduled hide.
 */
internal suspend fun awaitPanelHide(isActive: Boolean): Boolean {
    if (!isActive) {
        return false
    }
    delay(PLAYER_PANEL_HIDE_DELAY_MS)
    return true
}
