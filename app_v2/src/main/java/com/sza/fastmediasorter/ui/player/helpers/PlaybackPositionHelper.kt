package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Playback-position auto-save and seek utilities.
 *
 * Extension functions on [VideoPlayerManager] — extracted to a separate file to reduce
 * per-file CFG complexity for the Kotlin compiler (avoids GC overhead during parallel
 * flavor compilation of the original 1 700-line VideoPlayerManager.kt).
 */

// ═══════════════════════════════════════════════════════════════════════════
// Auto-save lifecycle
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Start auto-saving playback position every [VideoPlayerManager.POSITION_SAVE_INTERVAL_MS] ms.
 * Replaces any previously scheduled save loop.
 */
internal fun VideoPlayerManager.startPositionSaving() {
    stopPositionSaving()

    positionSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            retryHandler.postDelayed(this, VideoPlayerManager.POSITION_SAVE_INTERVAL_MS)
        }
    }

    retryHandler.postDelayed(positionSaveRunnable!!, VideoPlayerManager.POSITION_SAVE_INTERVAL_MS)
    Timber.d("VideoPlayerManager: Started position auto-save")
}

/** Cancel the auto-save loop started by [startPositionSaving]. */
internal fun VideoPlayerManager.stopPositionSaving() {
    positionSaveRunnable?.let {
        retryHandler.removeCallbacks(it)
        positionSaveRunnable = null
        Timber.d("VideoPlayerManager: Stopped position auto-save")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Immediate save
// ═══════════════════════════════════════════════════════════════════════════

/** Persist the current playback position to the database (skip if position unchanged). */
internal fun VideoPlayerManager.saveCurrentPosition() {
    val path = currentFilePath ?: return
    val player = exoPlayer ?: return

    val position = player.currentPosition
    val duration = player.duration

    // Skip save if position hasn't changed (e.g., paused) — avoids redundant DB writes
    if (position == lastSavedPosition) return

    if (duration > 0 && position >= 0) {
        lastSavedPosition = position
        managerScope.launch(Dispatchers.IO) {
            try {
                playbackPositionRepository.savePosition(path, position, duration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "VideoPlayerManager: Failed to save position")
            }
        }
        onPositionSaved?.invoke()
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Seek helpers
// ═══════════════════════════════════════════════════════════════════════════

fun VideoPlayerManager.seekForward(seconds: Int) {
    exoPlayer?.let { player ->
        val newPosition = (player.currentPosition + seconds * 1000).coerceAtMost(player.duration)
        player.seekTo(newPosition)
        Timber.d("VideoPlayerManager: Seek forward ${seconds}s to ${newPosition}ms")
    }
}

fun VideoPlayerManager.seekBackward(seconds: Int) {
    exoPlayer?.let { player ->
        val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0)
        player.seekTo(newPosition)
        Timber.d("VideoPlayerManager: Seek backward ${seconds}s to ${newPosition}ms")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Time formatting
// ═══════════════════════════════════════════════════════════════════════════

/** Format milliseconds as [H:]MM:SS. */
internal fun VideoPlayerManager.formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
