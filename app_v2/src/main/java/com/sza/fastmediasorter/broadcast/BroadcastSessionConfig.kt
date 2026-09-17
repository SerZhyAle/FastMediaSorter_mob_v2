package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.DEFAULT_BROADCAST_STREAM_TITLE

/**
 * Immutable snapshot of broadcast preferences read at session start. The capture service builds this
 * from [com.sza.fastmediasorter.domain.model.AppSettings] before opening the HTTP server or the
 * audio recorder, and every downstream component (server, encoder, descriptor) reads from the same
 * instance so metadata cannot drift from the encoder configuration.
 */
data class BroadcastSessionConfig(
    val streamTitle: String,
    val bitRateBps: Int,
    val port: Int,
    val sampleRateHz: Int,
    val channelCount: Int,
    // S2814: stable identity of the broadcasting phone. Null in DEFAULT; readSessionConfig fills
    // it from settings (generating and persisting a UUID on first broadcast). Flows into the
    // descriptor's sourceId so receivers recognise the same phone across address changes.
    val sourceDeviceId: String? = null,
    // S3038: video parameters for camera broadcast modes. Ignored by AUDIO_ONLY.
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoFps: Int = 30,
    val videoBitrateBps: Int = 2_000_000,
    val rtspPort: Int = 8554,
    // S3049: digital PCM microphone gain percentage (50% - 400%, default 100%).
    val micGainPercent: Int = 100,
) {
    companion object {
        val DEFAULT = BroadcastSessionConfig(
            streamTitle = DEFAULT_BROADCAST_STREAM_TITLE,
            bitRateBps = 128_000,
            port = 8768,
            sampleRateHz = 44_100,
            channelCount = 1,
        )
    }
}
