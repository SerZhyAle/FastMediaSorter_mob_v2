package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import timber.log.Timber

/**
 * Manages audio focus for StandalonePlayerActivity playback.
 *
 * Requests AUDIOFOCUS_GAIN on media start, releases on stop/destroy.
 * Notifies caller on transient/permanent focus loss via [onFocusLoss] lambda.
 *
 * API fork:
 *  - API 26+ (Android 8+): AudioFocusRequest.Builder
 *  - API 23–25 (legacy flavor): deprecated requestAudioFocus() overload
 *
 * Audio focus regain (AUDIOFOCUS_GAIN) is only logged — no auto-resume to avoid
 * surprising the user in a standalone "open single file" context (ADR-2).
 */
class AudioFocusManager(
    private val context: Context,
    private val onFocusLoss: (isPermanent: Boolean) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    var hasFocus: Boolean = false
        private set

    // Visible for testing
    internal val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Timber.d("AudioFocusManager: transient loss — pausing")
                onFocusLoss(false)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.d("AudioFocusManager: permanent loss — stopping")
                hasFocus = false
                onFocusLoss(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.d("AudioFocusManager: focus regained")
                // No auto-resume — caller is responsible (ADR-2)
            }
        }
    }

    fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestFocusApi26()
        } else {
            @Suppress("DEPRECATION")
            requestFocusLegacy()
        }
    }

    fun releaseFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        hasFocus = false
        Timber.d("AudioFocusManager: focus released")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestFocusApi26() {
        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attr)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioFocusRequest = request
        hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Timber.d("AudioFocusManager: requestFocus (API 26+) granted=$hasFocus")
    }

    @Suppress("DEPRECATION")
    private fun requestFocusLegacy() {
        hasFocus = audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Timber.d("AudioFocusManager: requestFocus (legacy) granted=$hasFocus")
    }
}
