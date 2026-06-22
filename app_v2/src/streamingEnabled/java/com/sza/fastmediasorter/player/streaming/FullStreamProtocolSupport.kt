package com.sza.fastmediasorter.player.streaming

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import javax.inject.Inject

/**
 * S0565: full stream-protocol support for streaming-capable flavors (standard / legacy / noLegal /
 * vr). HLS/DASH auto-detection works because their Media3 modules are on the classpath here; RTSP
 * is built explicitly. This is the only place [RtspMediaSource] is referenced, so lite/photos (which
 * lack the module) never see the symbol.
 */
@UnstableApi
class FullStreamProtocolSupport @Inject constructor() : StreamProtocolSupport {

    override val supportsSegmentedStreaming: Boolean = true

    override val supportsRtsp: Boolean = true

    override fun createRtspMediaSource(uri: Uri, dataSourceFactory: DataSource.Factory): MediaSource =
        // Force RTP-over-RTSP/TCP (interleaved): the most predictable transport across LAN/internet
        // and the iteration-1 baseline (UDP/multicast are deferred best-effort).
        RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(uri))
}
