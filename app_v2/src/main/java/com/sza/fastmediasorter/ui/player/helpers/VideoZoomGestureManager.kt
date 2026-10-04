package com.sza.fastmediasorter.ui.player.helpers

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import timber.log.Timber

/** Transforms only the video surface so transport controls and subtitles retain their size. */
class VideoZoomGestureManager(
    private val playerView: PlayerView
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private var scale = MIN_SCALE
    private var offsetX = 0f
    private var offsetY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var claimedSequence = false
    private var tapCandidate = false
    private var revealOnTap = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(playerView.context).scaledTouchSlop
    private var observer: ViewTreeObserver? = null
    private var keyHost: View? = null
    private var observedPlayer: Player? = null
    private var observedItem: MediaItem? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surface: View? = null

    private val detector = ScaleGestureDetector(
        playerView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                Timber.d("S4091: video pinch started")
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                changeScale(scale * detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }
    ).apply { isQuickScaleEnabled = false }

    private val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
        if (!isVideoActive() || event.action != KeyEvent.ACTION_DOWN) {
            false
        } else {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD -> zoomBy(ZOOM_STEP)
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> zoomBy(1f / ZOOM_STEP)
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                    reset()
                    true
                }
                else -> false
            }
        }
    }

    init {
        playerView.addOnAttachStateChangeListener(this)
        playerView.setOnGenericMotionListener { _, event ->
            if (isVideoActive() && event.actionMasked == MotionEvent.ACTION_SCROLL &&
                event.metaState and KeyEvent.META_CTRL_ON != 0
            ) {
                val scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                when {
                    scroll > 0f -> zoomBy(ZOOM_STEP)
                    scroll < 0f -> zoomBy(1f / ZOOM_STEP)
                    else -> false
                }
            } else {
                false
            }
        }
        if (playerView.isAttachedToWindow) onViewAttachedToWindow(playerView)
    }

    override fun onViewAttachedToWindow(view: View) {
        observer = playerView.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        keyHost = playerView.rootView.also { ViewCompat.addOnUnhandledKeyEventListener(it, keyListener) }
    }

    override fun onViewDetachedFromWindow(view: View) {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        keyHost?.let { ViewCompat.removeOnUnhandledKeyEventListener(it, keyListener) }
        keyHost = null
        reset()
        observedPlayer = null
        observedItem = null
        surface = null
    }

    fun release() {
        onViewDetachedFromWindow(playerView)
        playerView.removeOnAttachStateChangeListener(this)
        playerView.setOnGenericMotionListener(null)
    }

    override fun onPreDraw(): Boolean {
        val currentSurface = playerView.videoSurfaceView
        val player = playerView.player
        val item = player?.currentMediaItem
        val geometryChanged = surfaceWidth != (currentSurface?.width ?: 0) ||
            surfaceHeight != (currentSurface?.height ?: 0)
        val mediaChanged = observedPlayer !== player || observedItem != item
        if (surface !== currentSurface || mediaChanged || geometryChanged) {
            reset()
            surface = currentSurface
            observedPlayer = player
            observedItem = item
            surfaceWidth = currentSurface?.width ?: 0
            surfaceHeight = currentSurface?.height ?: 0
        }
        return true
    }

    /** A claimed multitouch sequence includes its tail, preventing a pinch from becoming a file tap. */
    fun handleTouchEvent(event: MotionEvent): Boolean =
        if (isVideoActive()) handleVideoTouchEvent(event) else false

    private fun handleVideoTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            claimedSequence = scale > MIN_SCALE
            tapCandidate = true
            revealOnTap = !playerView.isControllerFullyVisible
            downX = event.x
            downY = event.y
            lastX = event.x
            lastY = event.y
        }
        if (event.pointerCount > 1) {
            claimedSequence = true
            tapCandidate = false
        }
        if (kotlin.math.abs(event.x - downX) > touchSlop || kotlin.math.abs(event.y - downY) > touchSlop) {
            tapCandidate = false
        }
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && tapCandidate && revealOnTap) claimedSequence = true
        if (!claimedSequence) return false
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !detector.isInProgress) {
            offsetX += event.x - lastX
            offsetY += event.y - lastY
            applyTransform()
        }
        // Rebase after a finger lifts so the surviving finger cannot make the picture jump.
        val remainingIndex = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.actionIndex == 0) 1 else 0
        lastX = event.getX(remainingIndex)
        lastY = event.getY(remainingIndex)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (event.actionMasked == MotionEvent.ACTION_UP && tapCandidate) playerView.showController()
            claimedSequence = false
            tapCandidate = false
        }
        return true
    }

    private fun isVideoActive(): Boolean =
        playerView.visibility == View.VISIBLE && playerView.player?.videoSize?.width?.let { it > 0 } == true

    private fun zoomBy(factor: Float): Boolean {
        changeScale(scale * factor, playerView.width / CENTER_DIVISOR, playerView.height / CENTER_DIVISOR)
        return true
    }

    private fun changeScale(value: Float, focusX: Float, focusY: Float) {
        if (!value.isFinite()) return
        val newScale = value.coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = newScale / scale
        offsetX = offsetX * ratio + (focusX - playerView.width / CENTER_DIVISOR) * (1f - ratio)
        offsetY = offsetY * ratio + (focusY - playerView.height / CENTER_DIVISOR) * (1f - ratio)
        scale = newScale
        applyTransform()
    }

    private fun applyTransform() {
        val target = playerView.videoSurfaceView ?: return
        val maxX = target.width * (scale - MIN_SCALE) / CENTER_DIVISOR
        val maxY = target.height * (scale - MIN_SCALE) / CENTER_DIVISOR
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
        target.scaleX = scale
        target.scaleY = scale
        target.translationX = offsetX
        target.translationY = offsetY
    }

    private fun reset() {
        scale = MIN_SCALE
        offsetX = 0f
        offsetY = 0f
        claimedSequence = false
        tapCandidate = false
        revealOnTap = false
        applyTransform()
    }

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 5f
        private const val ZOOM_STEP = 1.25f
        private const val CENTER_DIVISOR = 2f
    }
}
