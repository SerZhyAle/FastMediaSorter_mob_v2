package com.sza.fastmediasorter.domain.model.link

/**
 * S0116 §5.1 pillar G/I: parsed streaming manifest reference produced by the HTML
 * sniffer (Phase 02) and consumed by the streaming downloader (Phase 03).
 *
 * `manifestUrl` is the absolute http(s) URL of the manifest file (`*.m3u8` for HLS,
 * `*.mpd` for DASH). `isDrmProtected` is set when a pre-flight check detects
 * `EXT-X-KEY METHOD=...` (HLS) or `<ContentProtection>` (DASH); the streaming
 * pipeline must surface a terminal `DrmBlocked` outcome instead of attempting a
 * download in that case.
 */
sealed class StreamingManifest {
    abstract val manifestUrl: String
    abstract val isDrmProtected: Boolean

    data class Hls(
        override val manifestUrl: String,
        override val isDrmProtected: Boolean = false,
    ) : StreamingManifest()

    data class Dash(
        override val manifestUrl: String,
        override val isDrmProtected: Boolean = false,
    ) : StreamingManifest()
}
