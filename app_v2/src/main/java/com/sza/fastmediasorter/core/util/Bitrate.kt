package com.sza.fastmediasorter.core.util

import android.content.Context
import com.sza.fastmediasorter.R
import java.util.Locale

private const val BITS_PER_KBIT = 1000.0
private const val KBIT_PER_MBIT = 1000.0

/**
 * Format a bitrate with a unit taken from resources, so the unit follows the UI locale the way the
 * number already does.
 *
 * Examples (ru): 320000 -> "320,0 кбит/с", 12340000 -> "12,34 Мбит/с"
 *
 * Callers whose label already carries the unit must not use this - they would render it twice.
 */
fun formatBitrate(context: Context, bitsPerSecond: Int): String {
    val kbit = bitsPerSecond / BITS_PER_KBIT
    if (kbit < KBIT_PER_MBIT) {
        val value = String.format(Locale.getDefault(), "%.1f", kbit)
        val kbitText = context.getString(R.string.unit_bitrate_kbps, value)
        return kbitText
    }
    val value = String.format(Locale.getDefault(), "%.2f", kbit / KBIT_PER_MBIT)
    val mbitText = context.getString(R.string.unit_bitrate_mbps, value)
    return mbitText
}
