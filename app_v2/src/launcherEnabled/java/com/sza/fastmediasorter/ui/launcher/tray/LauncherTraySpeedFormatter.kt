package com.sza.fastmediasorter.ui.launcher.tray

import java.util.Locale

enum class SpeedUnit {
    BYTES,
    KILOBYTES,
    MEGABYTES,
    GIGABYTES,
}

data class SpeedReadout(
    val value: String,
    val unit: SpeedUnit,
)

/**
 * Pure byte-rate formatter for the launcher tray speed readout.
 *
 * Scales on 1024 boundaries, formats values < 10 with one decimal place and >= 10 with none,
 * and uses Locale.US for stable decimal formatting across locales.
 */
object LauncherTraySpeedFormatter {

    private const val KB = 1024.0
    private const val MB = 1024.0 * 1024.0
    private const val GB = 1024.0 * 1024.0 * 1024.0
    private const val SINGLE_DECIMAL_LIMIT = 10.0

    fun format(bytesPerSecond: Double): SpeedReadout {
        val safeBps = if (bytesPerSecond < 0) 0.0 else bytesPerSecond
        val (scaledValue, unit) = when {
            safeBps >= GB -> (safeBps / GB) to SpeedUnit.GIGABYTES
            safeBps >= MB -> (safeBps / MB) to SpeedUnit.MEGABYTES
            safeBps >= KB -> (safeBps / KB) to SpeedUnit.KILOBYTES
            else -> safeBps to SpeedUnit.BYTES
        }

        val formattedValue = if (scaledValue < SINGLE_DECIMAL_LIMIT) {
            String.format(Locale.US, "%.1f", scaledValue)
        } else {
            String.format(Locale.US, "%.0f", scaledValue)
        }

        return SpeedReadout(formattedValue, unit)
    }
}
