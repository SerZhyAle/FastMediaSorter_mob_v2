package com.sza.fastmediasorter.ui.player.helpers

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.CountDownTimer
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.media3.common.Player
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Manages vinyl record playback indicator and sleep timer.
 *
 * Vinyl Indicator:
 * - Small rotating vinyl record in corner during audio playback
 * - ObjectAnimator rotation on ImageView with vinyl drawable
 * - Shows only when music actively playing; stops on pause
 * - Minimal performance impact (single rotation animation)
 *
 * Sleep Timer:
 * - Countdown -> pause playback + optional fade out
 * - Configurable durations (15, 30, 45, 60, 90, 120 min)
 * - UI: remaining time badge, cancel option
 */
class SleepTimerManager(
    private val vinylView: ImageView,
    private val sleepTimerBadge: TextView?,
    private val playerProvider: () -> Player?
) {
    private var rotationAnimator: ObjectAnimator? = null
    private var sleepTimer: CountDownTimer? = null
    private var remainingMillis: Long = 0L
    private var fadeOutAnimator: ValueAnimator? = null

    /** Whether the sleep timer is currently active */
    val isSleepTimerActive: Boolean
        get() = sleepTimer != null

    /** Remaining time in minutes (rounded up) */
    val remainingMinutes: Int
        get() = ((remainingMillis + 59999) / 60000).toInt()

    // ===== Vinyl Record Indicator =====

    /**
     * Start vinyl record rotation animation.
     * Call when audio playback starts/resumes.
     */
    fun startVinylAnimation() {
        vinylView.isVisible = true
        vinylView.setImageResource(R.drawable.ic_vinyl_record)

        if (rotationAnimator == null) {
            rotationAnimator = ObjectAnimator.ofFloat(vinylView, View.ROTATION, 0f, 360f).apply {
                duration = VINYL_ROTATION_DURATION_MS
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = LinearInterpolator()
            }
        }

        val animator = rotationAnimator ?: return
        if (animator.isPaused) {
            animator.resume()
        } else if (!animator.isRunning) {
            animator.start()
        }
        Timber.d("SleepTimerManager: vinyl animation started")
    }

    /**
     * Pause vinyl record rotation (when audio is paused).
     */
    fun pauseVinylAnimation() {
        rotationAnimator?.let { animator ->
            if (animator.isRunning) {
                animator.pause()
                Timber.d("SleepTimerManager: vinyl animation paused")
            }
        }
    }

    /**
     * Stop and hide vinyl record indicator.
     * Call when switching away from audio or stopping playback.
     */
    fun stopVinylAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        vinylView.isVisible = false
        vinylView.rotation = 0f
        Timber.d("SleepTimerManager: vinyl animation stopped")
    }

    /**
     * Update vinyl animation state based on player state.
     * @param isPlaying Whether audio is actively playing
     * @param isAudioFile Whether the current file is an audio file
     */
    fun updateVinylState(isPlaying: Boolean, isAudioFile: Boolean) {
        if (!isAudioFile) {
            stopVinylAnimation()
            return
        }
        if (isPlaying) {
            startVinylAnimation()
        } else {
            pauseVinylAnimation()
        }
    }

    // ===== Sleep Timer =====

    /**
     * Start sleep timer with given duration in minutes.
     * When timer expires: fade out audio and pause.
     */
    fun startSleepTimer(durationMinutes: Int) {
        cancelSleepTimer()
        val durationMillis = durationMinutes * 60 * 1000L
        remainingMillis = durationMillis

        Timber.d("SleepTimerManager: starting sleep timer for $durationMinutes min")

        sleepTimerBadge?.isVisible = true
        updateBadgeText()

        sleepTimer = object : CountDownTimer(durationMillis, TIMER_TICK_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateBadgeText()
            }

            override fun onFinish() {
                remainingMillis = 0
                Timber.d("SleepTimerManager: sleep timer expired, fading out")
                fadeOutAndPause()
            }
        }.start()
    }

    /**
     * Cancel the active sleep timer.
     */
    fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        remainingMillis = 0
        sleepTimerBadge?.isVisible = false
        fadeOutAnimator?.cancel()
        fadeOutAnimator = null

        // Restore volume if it was being faded out
        playerProvider()?.volume = 1.0f
        Timber.d("SleepTimerManager: sleep timer cancelled")
    }

    /**
     * Fade out audio volume over FADE_OUT_DURATION_MS, then pause.
     */
    private fun fadeOutAndPause() {
        val player = playerProvider() ?: run {
            sleepTimerBadge?.isVisible = false
            return
        }

        fadeOutAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
            duration = FADE_OUT_DURATION_MS
            addUpdateListener { animator ->
                val volume = animator.animatedValue as Float
                player.volume = volume
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    player.pause()
                    player.volume = 1.0f // Restore for next playback
                    sleepTimerBadge?.isVisible = false
                    sleepTimer = null
                    Timber.d("SleepTimerManager: fade out complete, playback paused")
                }
            })
            start()
        }
    }

    private fun updateBadgeText() {
        val minutes = remainingMinutes
        sleepTimerBadge?.text = if (minutes >= 60) {
            "${minutes / 60}h ${minutes % 60}m"
        } else {
            "${minutes}m"
        }
    }

    /**
     * Release all resources. Call from Activity.onDestroy().
     */
    fun release() {
        stopVinylAnimation()
        cancelSleepTimer()
    }

    companion object {
        /** Duration of one full vinyl rotation (ms) */
        private const val VINYL_ROTATION_DURATION_MS = 4000L

        /** Sleep timer tick interval (ms) — update badge every minute */
        private const val TIMER_TICK_INTERVAL_MS = 60_000L

        /** Fade out duration before pause (ms) */
        private const val FADE_OUT_DURATION_MS = 10_000L

        /** Available sleep timer durations (minutes) */
        val SLEEP_TIMER_OPTIONS = intArrayOf(15, 30, 45, 60, 90, 120)
    }
}
