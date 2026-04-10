package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.math.MathUtils
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Handles video touch gestures for StandalonePlayerActivity:
 *   - Vertical left  → screen brightness (0.05–1.0)
 *   - Vertical right → system volume
 *   - Horizontal     → fine seek scrub (±60s across full width)
 *   - Double-tap left/right → ±10s jump
 *   - Double-tap center    → play/pause toggle
 *   - Single-tap center    → show/hide ExoPlayer controller
 *
 * Adapted from VideoTouchDelegate; decoupled from PlayerActivity binding.
 */
class StandaloneVideoTouchDelegate(
    private val activity: Activity,
    private val playerView: PlayerView,
    private val rootView: View
) {

    private enum class GestureMode { NONE, BRIGHTNESS, VOLUME, SEEK }

    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val uiHandler = Handler(Looper.getMainLooper())

    private var gestureIndicator: android.widget.TextView? = null
    private val hideIndicatorRunnable = Runnable { gestureIndicator?.visibility = View.GONE }

    private var downX = 0f
    private var downY = 0f
    private var startBrightness = 0.5f
    private var startVolume = 0
    private var startPositionMs = 0L
    private var gestureMode = GestureMode.NONE

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                downX = e.x
                downY = e.y
                startBrightness = getCurrentBrightness()
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                startPositionMs = playerView.player?.currentPosition ?: 0L
                gestureMode = GestureMode.NONE
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = rootView.width.toFloat()
                if (width <= 0f) return false
                val leftBoundary = width * 0.35f
                val rightBoundary = width * 0.65f
                if (e.x > leftBoundary && e.x < rightBoundary) {
                    toggleController()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val player = playerView.player ?: return false
                val width = rootView.width.toFloat()
                if (width <= 0f) return false

                when {
                    e.x < width * 0.35f -> {
                        val newPos = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        showIndicator("⏪ 10s")
                    }
                    e.x > width * 0.65f -> {
                        val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                        val newPos = (player.currentPosition + 10_000L).coerceAtMost(duration)
                        player.seekTo(newPos)
                        showIndicator("⏩ 10s")
                    }
                    else -> {
                        if (player.isPlaying) {
                            player.pause()
                            showIndicator("⏸")
                        } else {
                            player.play()
                            showIndicator("▶")
                        }
                    }
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val width = rootView.width.toFloat()
                val height = rootView.height.toFloat()
                if (width <= 0f || height <= 0f) return false

                val deltaX = e2.x - downX
                val deltaY = e2.y - downY
                val absX = abs(deltaX)
                val absY = abs(deltaY)

                if (gestureMode == GestureMode.NONE) {
                    gestureMode = when {
                        absX > absY && absX > 18f -> GestureMode.SEEK
                        absY > absX && absY > 18f -> {
                            if (downX < width * 0.5f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                        }
                        else -> GestureMode.NONE
                    }
                }

                return when (gestureMode) {
                    GestureMode.BRIGHTNESS -> {
                        val delta = (-deltaY / height)
                        val newBrightness = MathUtils.clamp(startBrightness + delta, 0.05f, 1.0f)
                        applyBrightness(newBrightness)
                        showIndicator("☀ ${(newBrightness * 100f).roundToInt()}%")
                        true
                    }
                    GestureMode.VOLUME -> {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (maxVol <= 0) return false
                        val volumeDelta = ((-deltaY / height) * maxVol).roundToInt()
                        val newVolume = MathUtils.clamp(startVolume + volumeDelta, 0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                        val volumePercent = (newVolume * 100f / maxVol).roundToInt()
                        showIndicator("🔊 $volumePercent%")
                        true
                    }
                    GestureMode.SEEK -> {
                        val player = playerView.player ?: return false
                        val duration = player.duration
                        if (duration <= 0L) return false
                        val seekDeltaMs = ((deltaX / width) * 60_000f).toLong()
                        val target = (startPositionMs + seekDeltaMs).coerceIn(0L, duration)
                        player.seekTo(target)
                        val seconds = abs(seekDeltaMs / 1000L)
                        showIndicator(if (seekDeltaMs >= 0L) "⏩ ${seconds}s" else "⏪ ${seconds}s")
                        true
                    }
                    GestureMode.NONE -> false
                }
            }
        }
    )

    /** Attach the gesture indicator TextView (optional — found by ID in playerView). */
    fun attachIndicator(indicator: android.widget.TextView?) {
        gestureIndicator = indicator
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (!isVideoGestureArea(event)) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) {
                gestureMode = GestureMode.NONE
            }
            return false
        }
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gestureMode = GestureMode.NONE
            scheduleIndicatorHide()
        }
        return handled
    }

    private fun isVideoGestureArea(event: MotionEvent): Boolean {
        val playerHeight = playerView.height
        if (playerHeight <= 0) return true
        if (playerView.isControllerFullyVisible) {
            val gestureLimitY = (playerHeight * 0.68f).toInt()
            return event.y.toInt() < gestureLimitY
        }
        return true
    }

    private fun toggleController() {
        if (playerView.isControllerFullyVisible) {
            playerView.hideController()
        } else {
            playerView.showController()
        }
    }

    private fun getCurrentBrightness(): Float {
        val current = activity.window.attributes.screenBrightness
        return if (current in 0f..1f) current else 0.5f
    }

    private fun applyBrightness(value: Float) {
        try {
            val attrs = activity.window.attributes
            attrs.screenBrightness = value
            activity.window.attributes = attrs
        } catch (e: Exception) {
            timber.log.Timber.w(e, "StandaloneVideoTouchDelegate: brightness control failed")
        }
    }

    private fun showIndicator(text: String) {
        gestureIndicator?.let {
            it.text = text
            it.visibility = View.VISIBLE
            scheduleIndicatorHide()
        }
    }

    private fun scheduleIndicatorHide() {
        uiHandler.removeCallbacks(hideIndicatorRunnable)
        uiHandler.postDelayed(hideIndicatorRunnable, 900L)
    }
}
