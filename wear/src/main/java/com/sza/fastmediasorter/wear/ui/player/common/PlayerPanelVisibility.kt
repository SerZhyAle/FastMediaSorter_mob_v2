package com.sza.fastmediasorter.wear.ui.player.common

import kotlinx.coroutines.delay

/**
 * Waits out the panel delay, and answers whether the caller should now hide its controls.
 *
 * "Active" is each screen's own idea of playing: sound or frames for the audio and video players, a
 * running slideshow or open picture for the image viewer. Inactive means the countdown never
 * starts - a paused video keeps its transport.
 *
 * S2505: the duration now arrives from the caller via [delayMillis], read from the
 * [com.sza.fastmediasorter.wear.data.preferences.WearPreferencesRepository.panelAutoHideSeconds] setting.
 *
 * @param isActive whether the screen is currently active and eligible to hide controls.
 * @param delayMillis how long to wait before hiding controls, in milliseconds.
 * @return true when the delay elapsed and the panel should go; false when there was nothing to wait
 * for. A caller that is cancelled mid-delay never sees either, which is the intended way to abort a
 * scheduled hide.
 */
internal suspend fun awaitPanelHide(isActive: Boolean, delayMillis: Long): Boolean {
    if (!isActive) {
        return false
    }
    delay(delayMillis)
    return true
}

