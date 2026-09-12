package com.sza.fastmediasorter.wear.domain.netmonitor

private const val SIGNAL_FLOOR_DBM = -100
private const val SIGNAL_CEILING_DBM = -30

/** A sample never scales to nothing, because a zero-height bar reads as a missing sample. */
private const val SIGNAL_MIN_FRACTION = 0.08f

/**
 * Where a bar's height comes from. -100 dBm is the floor Android itself uses for "no usable signal"
 * and -30 the practical ceiling next to an access point; anything outside clamps rather than
 * disappearing.
 */
internal fun signalFraction(dbm: Int): Float {
    val span = (SIGNAL_CEILING_DBM - SIGNAL_FLOOR_DBM).toFloat()
    return ((dbm - SIGNAL_FLOOR_DBM) / span).coerceIn(SIGNAL_MIN_FRACTION, 1f)
}
