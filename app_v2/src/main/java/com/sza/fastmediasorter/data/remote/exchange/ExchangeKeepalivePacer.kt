package com.sza.fastmediasorter.data.remote.exchange

import android.os.SystemClock
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Paces the keepalive of a DEVICE-EXCHANGE control stream (7.3) in real time.
 *
 * The server counts its window of three intervals in wall-clock time, while `System.nanoTime` and a coroutine
 * `delay` stand still whenever the CPU is suspended. A phone that sleeps through most of the window therefore sent
 * no keepalive and read its own silence as a few seconds, so the server dropped the stream and the reconnect woke
 * the radio again, about every 96 s for as long as the phone idled. [now] defaults to `elapsedRealtime`, which
 * keeps counting through deep sleep.
 */
internal class ExchangeKeepalivePacer(
    private val intervalMs: Long,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val startedAt = now()

    @Volatile
    private var heardAt = startedAt

    @Volatile
    private var sentAt = startedAt

    /** Set before [pace] returns, so the read failure after the caller closes the stream is not blamed on the net. */
    @Volatile
    var ended = false
        private set

    /** Any frame from the server counts, not only the answer to a keepalive. */
    fun heard() {
        heardAt = now()
    }

    fun silentForMs(): Long = now() - heardAt

    fun sessionAgeMs(): Long = now() - startedAt

    /** A keepalive is owed once a whole interval of real time has passed since the last one. */
    fun due(): Boolean = now() - sentAt >= intervalMs

    /** The server answers every keepalive, so three silent intervals of real time mean a dead stream. */
    fun windowMissed(): Boolean = silentForMs() > intervalMs * WINDOW

    /**
     * Sends a keepalive through [send] whenever one is due, until the window is missed or [send] fails.
     *
     * The tick runs in awake time only and never wakes the device; what matters is that the first tick after a
     * wake finds the interval already spent in real time and sends at once.
     */
    suspend fun pace(send: () -> Boolean) {
        var alive = true
        while (alive) {
            delay(TICK_MS)
            alive = !due() || sendOwed(send)
        }
        ended = true
    }

    private fun sendOwed(send: () -> Boolean): Boolean {
        if (windowMissed() || !send()) return false
        Timber.d("S4815: keepalive sent, silent %d ms, last one %d ms ago", silentForMs(), now() - sentAt)
        sentAt = now()
        return true
    }

    companion object {
        /** The longest interval DEVICE-EXCHANGE 7.3 lets a client propose; the server's window is three of them. */
        const val PROPOSED_SECONDS = 120
        const val WINDOW = 3
        const val TICK_MS = 2_000L
    }
}
