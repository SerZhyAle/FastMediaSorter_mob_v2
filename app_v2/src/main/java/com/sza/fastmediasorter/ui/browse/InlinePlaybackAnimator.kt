package com.sza.fastmediasorter.ui.browse

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView

/**
 * Animates a playback indicator image - the inline-play button in MediaFileAdapter list items, and
 * the artwork slot of the background-playback bar. One instance per target view: it holds the
 * running ObjectAnimator references so they can be cancelled on recycle / state change.
 *
 * @param noteDurationMs one full turn of the note, in ms. The list keeps the default; the
 *   background-playback bar turns slower (S1382).
 */
class InlinePlaybackAnimator(
    private val target: ImageView,
    private val noteDurationMs: Long = DEFAULT_NOTE_DURATION_MS
) {

    private var noteAnimator: ObjectAnimator? = null
    private var downloadAnimator: ObjectAnimator? = null
    private var detachGuardArmed = false

    // S1302: these animators repeat INFINITE-ly, so a row that leaves the window with one running
    // (Back-press exit during inline playback) keeps requesting Choreographer frames until GC
    // happens to collect the animator's weak target. The guard is one-shot: it stops the animation
    // and unregisters itself, and every start() re-arms it.
    private val detachGuard = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            stopAll()
            v.removeOnAttachStateChangeListener(this)
            detachGuardArmed = false
        }
    }

    private fun armDetachGuard() {
        if (detachGuardArmed) return
        target.addOnAttachStateChangeListener(detachGuard)
        detachGuardArmed = true
    }

    fun startNote() {
        if (noteAnimator?.isRunning == true) return
        armDetachGuard()
        noteAnimator = ObjectAnimator.ofFloat(target, "rotation", 0f, 360f).apply {
            duration = noteDurationMs
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    /**
     * Freezes the note at its current angle. Unlike [stopNote] the animator stays alive, so
     * [resumeNote] continues the turn instead of snapping back to zero.
     */
    fun pauseNote() {
        noteAnimator?.pause()
    }

    /** Continues a note frozen by [pauseNote]. No-op when the animator is absent or already running. */
    fun resumeNote() {
        noteAnimator?.resume()
    }

    fun stopNote() {
        noteAnimator?.cancel()
        noteAnimator = null
        target.rotation = 0f
    }

    fun startDownload() {
        if (downloadAnimator?.isRunning == true) return
        armDetachGuard()
        downloadAnimator = ObjectAnimator.ofFloat(target, "alpha", 1f, 0.35f, 1f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun stopDownload() {
        downloadAnimator?.cancel()
        downloadAnimator = null
        target.alpha = 1f
    }

    fun stopAll() {
        stopNote()
        stopDownload()
        target.rotation = 0f
    }

    companion object {
        const val DEFAULT_NOTE_DURATION_MS = 1200L
    }
}
