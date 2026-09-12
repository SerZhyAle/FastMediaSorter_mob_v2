package com.sza.fastmediasorter.core.util

import android.os.SystemClock

/**
 * Monotonic time source for measured intervals.
 *
 * S1411 ADR-8: interval code reads this and never `System.currentTimeMillis()`. Wall-clock time is
 * moved by a manual time change or an NTP correction, which shifts a measurement already in flight and
 * can drive its elapsed value negative. The elapsed-realtime clock below is immune to both and keeps
 * counting in deep sleep, which is what a stopwatch sent to the background needs. Its readings are also
 * the base `RemoteViews.setChronometer` expects, so the screen and the home-screen widget share one
 * time base rather than converting between two.
 *
 * The interface exists so tests can advance time without waiting for it.
 */
fun interface ElapsedClock {
    fun nowMillis(): Long
}

/** The production reading. */
object SystemElapsedClock : ElapsedClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}
