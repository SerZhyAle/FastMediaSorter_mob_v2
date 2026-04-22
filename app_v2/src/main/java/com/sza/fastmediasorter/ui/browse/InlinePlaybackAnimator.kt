package com.sza.fastmediasorter.ui.browse

import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.widget.ImageView

/**
 * Animates the inline-play button in MediaFileAdapter list items.
 * One instance per ListViewHolder — holds the running ObjectAnimator references so they
 * can be cancelled on recycle / state change.
 */
class InlinePlaybackAnimator(private val target: ImageView) {

    private var noteAnimator: ObjectAnimator? = null
    private var downloadAnimator: ObjectAnimator? = null

    fun startNote() {
        if (noteAnimator?.isRunning == true) return
        noteAnimator = ObjectAnimator.ofFloat(target, "rotation", 0f, 360f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    fun stopNote() {
        noteAnimator?.cancel()
        noteAnimator = null
        target.rotation = 0f
    }

    fun startDownload() {
        if (downloadAnimator?.isRunning == true) return
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
}
