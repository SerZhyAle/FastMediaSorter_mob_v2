package com.sza.fastmediasorter.domain.files

import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Resolves filename collisions inside a target directory by appending the current
 * seconds component (`-ss`) to the base name (before the extension).
 *
 * Shared module owned by S0189 (Phase 09). Content-type agnostic — no extension or
 * filename pattern assumptions. Consumed by text notes (S0189) and drawings (S0191).
 */
object FileNameConflictResolver {

    private val SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

    /**
     * Returns a new filename with a `-ss` (seconds) suffix inserted before the last dot.
     *
     * Examples:
     *  - `note.txt`    → `note-42.txt`
     *  - `note`        → `note-42`
     *  - `my.note.txt` → `my.note-42.txt`
     *
     * @param originalName filename to decorate
     * @param now          epoch millis used to extract seconds (default: current time)
     * @param zone         time zone (default: device default)
     */
    fun applySecondsSuffix(
        originalName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val seconds = SECONDS_FORMATTER.format(dt)

        val dotIndex = originalName.lastIndexOf('.')
        return if (dotIndex > 0) {
            val stem = originalName.substring(0, dotIndex)
            val ext = originalName.substring(dotIndex)
            "$stem-$seconds$ext"
        } else {
            "$originalName-$seconds"
        }
    }

    /**
     * Returns the first name (from [intendedName]) that does not exist in [parentDir].
     * At most one suffix retry is performed; a double-collision within one second is
     * extremely unlikely and the remote write will fail-and-fallback gracefully.
     *
     * @return pair of (finalName, wasRenamed). `wasRenamed = true` means the suffix was applied.
     */
    fun resolveLocal(parentDir: File, intendedName: String): Pair<String, Boolean> {
        timber.log.Timber.d("S0189: FileNameConflictResolver.resolveLocal")
        if (!File(parentDir, intendedName).exists()) return Pair(intendedName, false)
        val suffixed = applySecondsSuffix(intendedName)
        return Pair(suffixed, true)
    }
}
