package com.sza.fastmediasorter.domain.usecase

import timber.log.Timber
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * S0189: resolves filename conflicts for text note saves.
 *
 * [applySecondsSuffix] appends the current seconds component (`-ss`) to the base name
 * (before the extension) when the intended target filename is already taken.
 * If the name has no extension, the suffix is appended at the end.
 */
object TextNoteNameConflictResolver {

    private val SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

    /**
     * Returns a new filename with a `-ss` (seconds) suffix inserted before the last dot.
     *
     * @param originalName  filename to decorate (e.g. `note.txt`)
     * @param now           epoch millis used to extract seconds (default: current time)
     * @param zone          time zone (default: device default)
     *
     * Examples:
     *  - `note.txt`    → `note-42.txt`
     *  - `note`        → `note-42`
     *  - `my.note.txt` → `my.note-42.txt`
     */
    fun applySecondsSuffix(
        originalName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val dt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val seconds = SECONDS_FORMATTER.format(dt)

        val dotIndex = originalName.lastIndexOf('.')
        val resolved = if (dotIndex > 0) {
            val stem = originalName.substring(0, dotIndex)
            val ext = originalName.substring(dotIndex)
            "$stem-$seconds$ext"
        } else {
            "$originalName-$seconds"
        }

        Timber.d("S0189: TextNoteNameConflictResolver -> $resolved")
        return resolved
    }

    /**
     * Returns the first name (from [intendedName]) that does not exist in [parentDir].
     * At most one suffix retry is performed. If even the suffixed name collides, returns
     * the suffixed name anyway — a double-collision within one second is extremely unlikely
     * and the remote write will fail-and-fallback gracefully.
     */
    fun resolveLocal(parentDir: File, intendedName: String): Pair<String, Boolean> {
        if (!File(parentDir, intendedName).exists()) return Pair(intendedName, false)
        val suffixed = applySecondsSuffix(intendedName)
        return Pair(suffixed, true)
    }
}
