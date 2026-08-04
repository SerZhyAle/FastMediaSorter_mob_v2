package com.sza.fastmediasorter.core.network

import java.net.HttpURLConnection

/**
 * S1298: `HttpURLConnection` defaults both timeouts to 0, which means "wait forever".
 *
 * A stalled connection with no RST (Wi-Fi dropped, VPN died, captive portal) then blocks the caller
 * indefinitely: cloud playback sits in STATE_BUFFERING with no error to recover from, and a
 * background transfer coroutine hangs until process death. Every raw `openConnection()` in the app
 * goes through [applyTimeouts] so a dead peer surfaces as an IOException the existing error paths
 * already handle.
 */
object HttpTimeouts {

    /** TCP/TLS handshake budget - a reachable host answers well inside this. */
    const val CONNECT_MS = 15_000

    /** Per-read budget for API calls returning JSON. */
    const val READ_MS = 30_000

    /** Per-read budget for media/file streaming, where a slow first byte is normal. */
    const val STREAM_READ_MS = 60_000

    /**
     * S1361: floor uplink assumed when budgeting an upload response wait.
     *
     * Half the ~26 KB/s measured on the mobile link that produced the reported failure, so the
     * derived budget carries a 2x margin over a link that slow.
     */
    const val UPLOAD_MIN_UPLINK_BYTES_PER_SEC = 16L * 1024L

    /** Hard ceiling on an upload response wait - keeps the S1298 guarantee that a dead peer cannot hang forever. */
    const val UPLOAD_READ_MAX_MS = 600_000

    private const val MILLIS_PER_SECOND = 1000L

    /**
     * S1361: read budget for the response to an upload request.
     *
     * An HTTP response cannot start arriving before the server has received the whole request body,
     * so the wait for the first response byte is proportional to body size divided by uplink speed.
     * A constant budget therefore fails every file above `uplink * budget` - on a mobile link that is
     * a couple of megabytes. Pass 0 or less when the length is unknown; the ceiling still applies.
     */
    fun uploadReadTimeoutMs(contentLengthBytes: Long): Int {
        if (contentLengthBytes <= 0L) return UPLOAD_READ_MAX_MS
        val transferMs = contentLengthBytes / UPLOAD_MIN_UPLINK_BYTES_PER_SEC * MILLIS_PER_SECOND
        return (STREAM_READ_MS + transferMs).coerceAtMost(UPLOAD_READ_MAX_MS.toLong()).toInt()
    }
}

/** Apply the standard connect/read budget. Pass [readTimeoutMs] for streaming reads. */
fun HttpURLConnection.applyTimeouts(readTimeoutMs: Int = HttpTimeouts.READ_MS) {
    connectTimeout = HttpTimeouts.CONNECT_MS
    readTimeout = readTimeoutMs
}
