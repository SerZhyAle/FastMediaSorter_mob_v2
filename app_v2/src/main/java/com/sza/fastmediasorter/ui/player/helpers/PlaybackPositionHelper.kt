package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import timber.log.Timber

/**
 * Playback-position auto-save and seek utilities.
 *
 * Extension functions on [VideoPlayerManager] - extracted to a separate file to reduce
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
    // S0854: stop the previous loop before dropping the reference - each PositionSaveLoop owns
    // its own Handler, so overwriting positionSaveLoop without stopping it first leaves the old
    // runnable self-reposting forever (orphaned, retains this VideoPlayerManager/PlayerActivity).
    positionSaveLoop?.stop()
    positionSaveLoop = PositionSaveLoop(
        intervalMs = VideoPlayerManager.POSITION_SAVE_INTERVAL_MS,
        getPath = { currentFilePath },
        getPositionMs = {
            val pos = exoPlayer?.currentPosition ?: -1L
            // Track the last genuine playback position (not a transient mid-seek value) so a
            // seek that crashes the extractor can resume in place instead of skipping the file.
            // Read here, on the main thread: ExoPlayer must not be touched from onSave, which
            // runs on Dispatchers.IO and would throw "Player is accessed on the wrong thread".
            if (pos > 0L && exoPlayer?.isPlaying == true) lastGoodPositionMs = pos
            pos
        },
        getDurationMs = { exoPlayer?.duration ?: -1L },
        scope = managerScope,
        onSave = { path, pos, dur ->
            playbackPositionRepository.savePosition(path, pos, dur)
        }
    )
    positionSaveLoop!!.start()
    Timber.d("VideoPlayerManager: Started position auto-save")
}

/** Cancel the auto-save loop started by [startPositionSaving]. */
internal fun VideoPlayerManager.stopPositionSaving() {
    positionSaveLoop?.stop()
    positionSaveLoop = null
    Timber.d("VideoPlayerManager: Stopped position auto-save")
}

// ═══════════════════════════════════════════════════════════════════════════
// Immediate save
// ═══════════════════════════════════════════════════════════════════════════

/** Persist the current playback position to the database (skip if position unchanged). */
internal fun VideoPlayerManager.saveCurrentPosition() {
    positionSaveLoop?.saveNow()
    onPositionSaved?.invoke()
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

/** Format milliseconds as [H:]MM:SS - delegates to [PlaybackPositionRestorer.formatTimeMs]. */
internal fun VideoPlayerManager.formatTime(millis: Long): String =
    PlaybackPositionRestorer.formatTimeMs(millis)
