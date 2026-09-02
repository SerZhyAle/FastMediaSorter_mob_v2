package com.sza.fastmediasorter.ui.launcher.gadget

/**
 * S2031: what the two faces of the stream window cell share - its footprints and the rule that picks
 * between them.
 *
 * Its own file rather than a companion inside the gadget: the placement write reads the same two spans
 * before any view exists, and a constant the caller reads out of the callee is a constant nobody finds
 * (same reason [MediaWindow] is a file).
 */
internal object StreamWindow {

    /** A radio cell is a square, like every media window: a strip cannot hold a transport row. */
    const val AUDIO_SPAN = 2

    /** Strategic §3.4: the video cell is at least three cells wide.. */
    const val VIDEO_SPAN_W = 3

    /** .. and two tall. */
    const val VIDEO_SPAN_H = 2

    /** Matches the media windows: a transport state change is not worth a tighter loop on a desktop. */
    const val POLL_MS = 2_000L

    /**
     * Whether a catalog channel is shown as a player rather than as a transport row.
     *
     * RTSP counts as video for the same reason the channel picker's video filter accepts it - the
     * catalog stores it as a third kind, and every RTSP source in it is a camera or a TV feed.
     */
    fun isVideoKind(mediaKind: String): Boolean =
        mediaKind.equals(KIND_VIDEO, ignoreCase = true) || mediaKind.equals(KIND_RTSP, ignoreCase = true)

    /**
     * The footprint a cell bound to this channel is placed at, as `width to height`.
     *
     * Lives here rather than inline in the placement so a test can assert the shipped decision instead of
     * a copy of it.
     */
    fun spanFor(mediaKind: String): Pair<Int, Int> =
        if (isVideoKind(mediaKind)) VIDEO_SPAN_W to VIDEO_SPAN_H else AUDIO_SPAN to AUDIO_SPAN

    private const val KIND_VIDEO = "VIDEO"
    private const val KIND_RTSP = "RTSP"
}
