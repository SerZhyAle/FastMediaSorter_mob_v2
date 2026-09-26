package com.sza.fastmediasorter.ui.player.letterbox

import android.os.SystemClock
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives LETTERBOX-HALO growth (rules 3-6, 10) on one [LetterboxFrameDrawable]: progress from the
 * clock, a frame at most every 15 ms, the last frame exactly at rest. Main thread only.
 */
class LetterboxHaloGrowthManager(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = SystemClock::uptimeMillis,
) {

    private var job: Job? = null
    private var growing: LetterboxFrameDrawable? = null

    /** [durationMs] is taken once here, so a speed change mid-flight lands on the next image (rule 5). */
    fun start(drawable: LetterboxFrameDrawable, durationMs: Double) {
        stop()
        growing = drawable
        drawable.onDrawFailure = { finish(drawable) }
        drawable.eased = 0.0
        job = scope.launch(Dispatchers.Main) {
            val startedAt = clockMs()
            while (isActive) {
                val progress = LetterboxFillMath.progressAt((clockMs() - startedAt).toDouble(), durationMs)
                if (progress >= 1.0) break
                drawable.eased = LetterboxFillMath.haloEase(progress)
                delay(LetterboxFillMath.FRAME_INTERVAL_MS)
            }
            if (isActive) finish(drawable)
        }
    }

    /**
     * Rule 10: a new image or a cleared surface stops the growth before it can draw into a frame
     * that is being replaced. The drawable keeps its current state; the caller decides whether it
     * becomes an underlay or is dropped.
     */
    fun stop() {
        job?.cancel()
        job = null
        growing?.onDrawFailure = null
        growing = null
    }

    /** Settles [drawable] at rest (rule 1) and forgets it. */
    private fun finish(drawable: LetterboxFrameDrawable) {
        if (growing === drawable) {
            job?.cancel()
            job = null
            growing = null
        }
        drawable.onDrawFailure = null
        drawable.settle()
    }
}
