package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

class AnimatedImageController {

    private var currentAnimatedDrawable: Animatable? = null
    private var currentTargetView: ImageView? = null
    private var pausedByLifecycle: Boolean = false
    private var pausedByUser: Boolean = false

    fun isAnimatedContent(mediaFile: MediaFile?, path: String): Boolean {
        if (mediaFile?.type == MediaType.GIF) return true
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".gif") ||
            lowerPath.endsWith(".webp") ||
            lowerPath.endsWith(".apng")
    }

    fun onDrawableLoaded(drawable: Drawable, targetView: ImageView?) {
        val animatable = drawable as? Animatable

        if (animatable == null) {
            clearCurrentAnimation()
            return
        }

        if (targetView !is PhotoView) {
            Timber.w("AnimatedImageController: Animated drawable loaded into non-PhotoView target")
        }

        if (currentAnimatedDrawable != null && currentAnimatedDrawable !== animatable) {
            currentAnimatedDrawable?.stop()
        }

        currentAnimatedDrawable = animatable
        currentTargetView = targetView

        if (!pausedByLifecycle && !pausedByUser) {
            currentAnimatedDrawable?.start()
        } else {
            currentAnimatedDrawable?.stop()
        }

        Timber.d("AnimatedImageController: Animated drawable attached (pausedByLifecycle=$pausedByLifecycle, pausedByUser=$pausedByUser)")
    }

    fun onLoadFailed() {
        clearCurrentAnimation()
    }

    fun onPause() {
        pausedByLifecycle = true
        currentAnimatedDrawable?.stop()
    }

    fun onResume() {
        pausedByLifecycle = false
        if (!pausedByUser) {
            currentAnimatedDrawable?.start()
        }
    }

    fun prepareForNewContent() {
        pausedByUser = false
    }

    fun hasAnimatedDrawable(): Boolean = currentAnimatedDrawable != null

    fun isPlaybackPaused(): Boolean = pausedByUser

    fun togglePlayback(): Boolean? {
        val drawable = currentAnimatedDrawable ?: return null
        pausedByUser = !pausedByUser

        if (pausedByUser || pausedByLifecycle) {
            drawable.stop()
        } else {
            drawable.start()
        }

        Timber.d("AnimatedImageController: togglePlayback pausedByUser=$pausedByUser pausedByLifecycle=$pausedByLifecycle")
        return pausedByUser
    }

    fun release() {
        clearCurrentAnimation()
        pausedByLifecycle = false
        pausedByUser = false
    }

    private fun clearCurrentAnimation() {
        currentAnimatedDrawable?.stop()
        currentAnimatedDrawable = null
        currentTargetView = null
    }
}
