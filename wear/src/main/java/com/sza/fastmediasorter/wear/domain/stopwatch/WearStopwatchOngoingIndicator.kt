package com.sza.fastmediasorter.wear.domain.stopwatch

/**
 * S3555: the stopwatch's ongoing activity - the watch face indicator, the Recents chip and what the tile
 * points at - as the session sees it.
 */
interface WearStopwatchOngoingIndicator {

    /** Shows the running measurement; answers whether the platform was actually handed a notification. */
    fun show(state: WearStopwatchState): Boolean

    fun hide()

    /**
     * True only where a runtime request could restore the indicator: API 33+ with the notification
     * permission not granted. Notifications switched off in system settings are not this case.
     */
    fun blockedByMissingPermission(): Boolean
}
