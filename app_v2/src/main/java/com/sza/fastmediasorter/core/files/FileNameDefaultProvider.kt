package com.sza.fastmediasorter.core.files

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Produces a timestamp-based default filename in the form `yy-MM-dd_HH-mm.<extension>`.
 *
 * Shared module owned by S0189; consumed by S0191 for drawings (extension `png`) and any
 * future content type that wants the same timestamp convention.
 *
 * The [extension] is the raw extension WITHOUT the leading dot (e.g. `"txt"`, `"png"`).
 * An empty extension yields a name without a dot.
 */
class FileNameDefaultProvider(
    private val extension: String,
) {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm")

    /**
     * @param now  epoch millis to base the name on (default: current time)
     * @param zone time zone (default: device default)
     * @return filename like `26-05-16_14-32.txt` (or `26-05-16_14-32` when [extension] is empty)
     */
    fun defaultName(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val stem = formatter.format(dt)
        return if (extension.isEmpty()) stem else "$stem.$extension"
    }
}
