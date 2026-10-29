package com.sza.fastmediasorter.wear.ui.player.common

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.sza.fastmediasorter.wear.domain.playback.WearStreamFailure
import com.sza.fastmediasorter.wear.domain.playback.WearStreamFailurePolicy

/** A cause chain this deep is a cycle or a wrapper storm, not a diagnosis. */
private const val MAX_CAUSE_DEPTH = 10

/**
 * S4473: what this player error means for a stream - a source that failed, or a producer that stopped.
 *
 * ExoPlayer wraps the network failure in a [PlaybackException] and keeps the HTTP rejection or the
 * refused socket several causes down, so the chain is walked for the status before the pure policy
 * judges it.
 */
internal fun PlaybackException.streamFailure(): WearStreamFailure {
    var current: Throwable? = cause
    var depth = 0
    var httpStatus: Int? = null
    while (current != null && httpStatus == null && depth < MAX_CAUSE_DEPTH) {
        httpStatus = (current as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        current = current.cause
        depth++
    }
    return WearStreamFailurePolicy.ofError(httpStatus, cause)
}
