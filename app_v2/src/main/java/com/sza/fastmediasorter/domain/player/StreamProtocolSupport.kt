package com.sza.fastmediasorter.domain.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource

/**
 * S0565: flavor-gated capability for segmented (HLS/DASH) and RTSP stream playback.
 *
 * The real implementation lives in the `streamingEnabled` source set (standard / legacy /
 * noLegal / vr), where the Media3 HLS/DASH/RTSP modules are on the classpath; `streamingDisabled`
 * (lite / photos) gets a progressive-only implementation. Keeping the `RtspMediaSource` reference
 * out of `src/main` lets lite/photos compile without the RTSP module.
 */
interface StreamProtocolSupport {

    /** True when the HLS/DASH Media3 modules are present (segmented streams can be auto-detected). */
    val supportsSegmentedStreaming: Boolean

    /** True when the RTSP module is present. */
    val supportsRtsp: Boolean

    /**
     * Builds an RTSP [MediaSource] for [uri], or null when RTSP is unsupported in this flavor.
     * Implementations baseline RTP-over-RTSP/TCP (interleaved) for predictable LAN/internet delivery.
     */
    fun createRtspMediaSource(uri: Uri, dataSourceFactory: DataSource.Factory): MediaSource?
}
