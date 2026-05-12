package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

/**
 * Suspend utility for restoring saved playback position before playback starts.
 *
 * Reads from the repository on IO, shows a resume toast on Main.
 * Returns the saved position in ms (0 if none). Caller is responsible for seekTo.
 */
object PlaybackPositionRestorer {

    /**
     * Read the saved position for [path], show a resume toast, and return the position.
     * Returns 0 if no saved position exists.
     *
     * Must be called from a coroutine context.
     */
    suspend fun restoreAndNotify(
        path: String,
        repository: PlaybackPositionRepository,
        context: Context,
        resumedFromStringResId: Int
    ): Long {
        val pos = withContext(Dispatchers.IO) { repository.getPosition(path) } ?: 0L
        if (pos > 0L) {
            val timeStr = formatTimeMs(pos)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(resumedFromStringResId, timeStr),
                    Toast.LENGTH_SHORT
                ).show()
            }
            Timber.d("PlaybackPositionRestorer: restored %dms for path=%s", pos, path)
        }
        return pos
    }

    /** Format milliseconds as [H:]MM:SS. */
    fun formatTimeMs(millis: Long): String {
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
}
