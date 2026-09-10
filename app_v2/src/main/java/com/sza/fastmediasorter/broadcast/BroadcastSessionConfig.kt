package com.sza.fastmediasorter.broadcast

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
) {
    companion object {
        val DEFAULT = BroadcastSessionConfig(
            streamTitle = "Phone Audio Stream",
            bitRateBps = 128_000,
            port = 8768,
            sampleRateHz = 44_100,
            channelCount = 1,
        )
    }
}
