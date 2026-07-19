package com.sza.fastmediasorter.core.playback

import androidx.media3.exoplayer.DefaultLoadControl

/** Centralized radio-stream buffering and recovery limits. */
object RadioStreamBufferConfig {
    const val MIN_BUFFER_MS = 12_000
    const val MAX_BUFFER_MS = 24_000
    const val BUFFER_FOR_PLAYBACK_MS = 4_000
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 6_000

    const val DIALOG_TIMEOUT_MS = 15_000L
    const val PASSIVE_STATUS_TIMEOUT_MS = 20_000L

    const val BASE_RETRY_DELAY_MS = 2_000L
    const val MAX_RETRY_DELAY_MS = 8_000L

    fun createLoadControl(): DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            MIN_BUFFER_MS,
            MAX_BUFFER_MS,
            BUFFER_FOR_PLAYBACK_MS,
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .build()
}
