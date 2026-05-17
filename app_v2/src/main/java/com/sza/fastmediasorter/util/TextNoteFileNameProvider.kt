package com.sza.fastmediasorter.util

import timber.log.Timber
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Produces the default filename for a new text note: `yy-MM-dd_HH-mm.txt`.
 * Two-digit year, two-digit month, two-digit day, underscore, 24-hour HH, hyphen, MM, .txt extension.
 */
object TextNoteFileNameProvider {

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm")

    /**
     * @param now     epoch millis to base the name on (default: current time)
     * @param zone    time zone (default: device default)
     * @return filename like `26-05-16_14-32.txt`
     */
    fun defaultName(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val dt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(now), zone
        )
        val name = FORMATTER.format(dt) + ".txt"
        Timber.d("S0189: TextNoteFileNameProvider.defaultName -> $name")
        return name
    }
}
