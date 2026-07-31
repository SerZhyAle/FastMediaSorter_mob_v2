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
}

/** Apply the standard connect/read budget. Pass [readTimeoutMs] for streaming reads. */
fun HttpURLConnection.applyTimeouts(readTimeoutMs: Int = HttpTimeouts.READ_MS) {
    connectTimeout = HttpTimeouts.CONNECT_MS
    readTimeout = readTimeoutMs
}
