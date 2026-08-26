package com.sza.fastmediasorter.wear.domain.files

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Mirrors `app_v2`'s `FileNameConflictResolver` - a `-ss` seconds suffix before the last dot, with at
 * most one retry.
 *
 * A mirror rather than a shared class for the same reason `WEAR_FILE_TRANSFER_MAX_BYTES` is pinned on
 * both sides: the two modules compile separately with no common artifact between them. The two copies
 * must move together, and `WearFileCapabilityPolicyTest` pins the shape so a divergence is loud.
 */
object WearFileNameConflictResolver {

    private val SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

    /** `note.txt` becomes `note-42.txt`; an extensionless `note` becomes `note-42`. */
    fun applySecondsSuffix(
        originalName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val seconds = SECONDS_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone))
        val dotIndex = originalName.lastIndexOf('.')
        return if (dotIndex > 0) {
            "${originalName.substring(0, dotIndex)}-$seconds${originalName.substring(dotIndex)}"
        } else {
            "$originalName-$seconds"
        }
    }

    /**
     * The first free name for [intendedName] in [parentDir], and whether a suffix had to be applied.
     *
     * One retry only: a second collision inside the same second is not worth a loop, and the caller
     * reports the write failure it would hit instead of spinning.
     */
    fun resolveLocal(parentDir: File, intendedName: String): Pair<String, Boolean> {
        if (!File(parentDir, intendedName).exists()) return Pair(intendedName, false)
        return Pair(applySecondsSuffix(intendedName), true)
    }
}
