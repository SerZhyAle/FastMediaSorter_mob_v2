package com.sza.fastmediasorter.ui.player.helpers

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Reusable periodic save loop for playback position.
 *
 * Encapsulates the Handler–Runnable scheduling, skip-if-unchanged guard, and IO dispatch.
 * Caller supplies lambdas that read the live player state at each tick.
 *
 * Thread-safety: [start], [stop], and [saveNow] must be called from the main thread.
 */
class PositionSaveLoop(
    private val intervalMs: Long,
    private val getPath: () -> String?,
    private val getPositionMs: () -> Long,
    private val getDurationMs: () -> Long,
    private val scope: CoroutineScope,
    private val onSave: suspend (path: String, positionMs: Long, durationMs: Long) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var lastSaved: Long = -1L

    /** Start periodic save. Resets the skip-if-unchanged guard. */
    fun start() {
        stop()
        lastSaved = -1L
        runnable = object : Runnable {
            override fun run() {
                saveNow()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(runnable!!, intervalMs)
        Timber.d("PositionSaveLoop: started interval=${intervalMs}ms")
    }

    /** Cancel the periodic save. */
    fun stop() {
        runnable?.let {
            handler.removeCallbacks(it)
            runnable = null
            Timber.d("PositionSaveLoop: stopped")
        }
    }

    /** Persist current position immediately (respects skip-if-unchanged guard). */
    fun saveNow() {
        val path = getPath() ?: return
        val position = getPositionMs()
        val duration = getDurationMs()
        if (position == lastSaved) return
        if (duration <= 0 || position < 0) return
        lastSaved = position
        scope.launch(Dispatchers.IO) {
            try {
                onSave(path, position, duration)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "PositionSaveLoop: failed to save position for path=%s", path)
            }
        }
    }
}
