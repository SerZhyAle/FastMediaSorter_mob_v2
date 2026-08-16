package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.math.MathUtils
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.PlayerActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Video-only touch delegate (Track F.1):
 * - Vertical left: brightness
 * - Vertical right: volume
 * - Horizontal: fine scrubbing
 * - Double tap: rewind/forward/play-pause
 * - Single tap center: custom controller toggle
 */
class VideoTouchDelegate(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding
) {

    private enum class GestureMode {
        NONE,
        BRIGHTNESS,
        VOLUME,
        SEEK
    }

    private val audioManager =
        activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideIndicatorRunnable = Runnable {
        binding.tvVideoGestureIndicator?.visibility = android.view.View.GONE
    }

    private var downX = 0f
    private var downY = 0f
    private var startBrightnessProgress = 50
    private var startVolume = 0
    private var startPositionMs = 0L
    private var gestureMode = GestureMode.NONE

    private val gestureDetector = GestureDetector(
        activity,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                downX = e.x
                downY = e.y
                startBrightnessProgress = getCurrentBrightnessProgress()
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                startPositionMs = binding.playerView.player?.currentPosition ?: 0L
                gestureMode = GestureMode.NONE
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = binding.root.width.toFloat()
                if (width <= 0f) return false

                val leftBoundary = width * 0.35f
                val rightBoundary = width * 0.65f
                if (e.x > leftBoundary && e.x < rightBoundary) {
                    togglePlayerController()
                    return true
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val player = binding.playerView.player ?: return false
                val width = binding.root.width.toFloat()
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
                val width = binding.root.width.toFloat()
                val height = binding.root.height.toFloat()
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

                when (gestureMode) {
                    GestureMode.BRIGHTNESS -> {
                        val brightnessDelta = ((-deltaY / height) * 100f).roundToInt()
                        val newBrightnessProgress = MathUtils.clamp(startBrightnessProgress + brightnessDelta, 0, 100)
                        applyBrightnessProgress(newBrightnessProgress)
                        showIndicator(
                            "☀ ${String.format(Locale.US, "%+d%%", activity.videoPlayerManager.getBrightnessPercentOffset())}"
                        )
                        return true
                    }

                    GestureMode.VOLUME -> {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        if (maxVol <= 0) return false
                        val volumeDelta = ((-deltaY / height) * maxVol).roundToInt()
                        val newVolume = MathUtils.clamp(startVolume + volumeDelta, 0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                        val volumePercent = (newVolume * 100f / maxVol).roundToInt()
                        showIndicator("🔊 $volumePercent%")
                        return true
                    }

                    GestureMode.SEEK -> {
                        val player = binding.playerView.player ?: return false
                        val duration = player.duration
                        if (duration <= 0L) return false

                        // Fine scrub: full-width drag ~= ±60 seconds
                        val seekDeltaMs = ((deltaX / width) * 60_000f).toLong()
                        val target = (startPositionMs + seekDeltaMs).coerceIn(0L, duration)
                        player.seekTo(target)
                        val seconds = abs(seekDeltaMs / 1000L)
                        if (seekDeltaMs >= 0L) {
                            showIndicator("⏩ ${seconds}s")
                        } else {
                            showIndicator("⏪ ${seconds}s")
                        }
                        return true
                    }

                    GestureMode.NONE -> return false
                }
            }
        }
    )

    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (!isVideoGestureArea(event)) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                gestureMode = GestureMode.NONE
            }
            return false
        }

        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gestureMode = GestureMode.NONE
            scheduleIndicatorHide()
        }
        return handled
    }

    private fun isVideoGestureArea(event: MotionEvent): Boolean {
        val playerHeight = binding.playerView.height
        if (playerHeight <= 0) return true

        // When controller is visible, reserve lower area for controller button taps.
        if (binding.playerView.isControllerFullyVisible) {
            val gestureLimitY = (playerHeight * 0.68f).toInt()
            return event.y.toInt() < gestureLimitY
        }

        return true
    }

    private fun togglePlayerController() {
        if (binding.playerView.isControllerFullyVisible) {
            binding.playerView.hideController()
        } else {
            binding.playerView.showController()
        }
    }

    private fun getCurrentBrightnessProgress(): Int = activity.videoPlayerManager.getBrightnessProgress()

    private fun applyBrightnessProgress(progress: Int) = activity.videoPlayerManager.setBrightnessProgress(progress)

    private fun showIndicator(text: String) {
        binding.tvVideoGestureIndicator?.text = text
        binding.tvVideoGestureIndicator?.visibility = android.view.View.VISIBLE
        scheduleIndicatorHide()
    }

    private fun scheduleIndicatorHide() {
        uiHandler.removeCallbacks(hideIndicatorRunnable)
        uiHandler.postDelayed(hideIndicatorRunnable, 900L)
    }
}
