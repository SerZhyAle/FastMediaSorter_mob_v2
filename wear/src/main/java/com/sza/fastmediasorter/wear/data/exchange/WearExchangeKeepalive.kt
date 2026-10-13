package com.sza.fastmediasorter.wear.data.exchange

/**
 * The presence rules of contract DEVICE-EXCHANGE 7.3, apart from the sockets: the server answers every keepalive,
 * so three silent intervals mean a dead stream that TCP alone could take many minutes to notice.
 */
object WearExchangeKeepalive {

    const val KEEPALIVE_SECONDS = 30
    const val WINDOW = 3
    const val INITIAL_BACKOFF_MS = 2_000L
    const val MAX_BACKOFF_MS = 120_000L

    private const val MIN_KEEPALIVE_SECONDS = 10
    private const val MAX_KEEPALIVE_SECONDS = 120
    private const val MILLIS_PER_SECOND = 1_000L

    /** A dead token or a revoked device stays wrong on every retry; the other reasons are transient. */
    val PERMANENT_REFUSALS: Set<String> = setOf("bad-credentials", "device-revoked")

    /** The interval the server echoed, clamped as 7.3 clamps it; the proposal when the server echoed none. */
    fun intervalMs(echoedSeconds: Int?): Long =
        (echoedSeconds ?: KEEPALIVE_SECONDS).coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS) * MILLIS_PER_SECOND

    fun isDead(silentForMs: Long, intervalMs: Long): Boolean = silentForMs > intervalMs * WINDOW

    fun nextBackoffMs(currentMs: Long): Long = (currentMs * 2).coerceIn(INITIAL_BACKOFF_MS, MAX_BACKOFF_MS)
}
