package com.sza.fastmediasorter.player.streaming

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import com.sza.fastmediasorter.domain.player.StreamProtocolSupport
import javax.inject.Inject

/**
 * S0565: progressive-only stream-protocol support for lite/photos, where the Media3 HLS/DASH/RTSP
 * modules are absent. Progressive http(s) audio still plays through the caller's
 * `DefaultMediaSourceFactory` (core extractors); segmented and RTSP streams are reported
 * unsupported so the UI can show a clear "not supported in this build" message.
 */
class ProgressiveOnlyStreamProtocolSupport @Inject constructor() : StreamProtocolSupport {

    override val supportsSegmentedStreaming: Boolean = false

    override val supportsRtsp: Boolean = false

    override fun createRtspMediaSource(uri: Uri, dataSourceFactory: DataSource.Factory): MediaSource? = null
}
