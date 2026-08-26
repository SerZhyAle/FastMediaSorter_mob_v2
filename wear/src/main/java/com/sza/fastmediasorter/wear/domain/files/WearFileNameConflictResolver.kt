package com.sza.fastmediasorter.wear.domain.files

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Mirrors `app_v2`'s `FileNameConflictResolver` - a `-ss` seconds suffix before the last dot.
 *
 * A mirror rather than a shared class for the same reason `WEAR_FILE_TRANSFER_MAX_BYTES` is pinned on
 * both sides: the two modules compile separately with no common artifact between them. The two copies
 * must move together, and `WearFileCapabilityPolicyTest` pins the shape so a divergence is loud.
 *
 * **One deliberate divergence (S1863):** this copy keeps looking until the name it returns is free,
 * where `app_v2` stops after the seconds suffix. That single retry was safe there because every
 * caller creates one file at a time from a name the user just typed. This module renames a whole
 * selection in one loop, so two files of one batch resolve inside the same second, receive the same
 * suffix, and `File.renameTo` - which replaces its destination rather than failing - would delete the
 * first file's bytes under the second. The suffix rule the strategic spec points at is unchanged; only
 * the promise that the result is actually free is new.
 */
object WearFileNameConflictResolver {

    private val SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ss")

    /** The first counter appended when even the seconds-suffixed name is taken. */
    private const val FIRST_INDEX = 2

    /** `note.txt` becomes `note-42.txt`; an extensionless `note` becomes `note-42`. */
    fun applySecondsSuffix(
        originalName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val seconds = SECONDS_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone))
        return insertBeforeExtension(originalName, seconds)
    }

    /**
     * The first free name for [intendedName] in [parentDir], and whether a suffix had to be applied.
     *
     * The returned name is guaranteed not to exist in [parentDir] at the moment of the call, so a
     * caller renaming onto it cannot silently replace another file.
     */
    fun resolveLocal(parentDir: File, intendedName: String): Pair<String, Boolean> {
        if (!File(parentDir, intendedName).exists()) {
            return Pair(intendedName, false)
        }
        val suffixed = applySecondsSuffix(intendedName)
        val free = if (File(parentDir, suffixed).exists()) {
            firstFreeIndexed(parentDir, suffixed)
        } else {
            suffixed
        }
        return Pair(free, true)
    }

    /** Walks `-2`, `-3`, .. past every name the directory already holds. */
    private fun firstFreeIndexed(parentDir: File, suffixedName: String): String {
        var index = FIRST_INDEX
        while (File(parentDir, insertBeforeExtension(suffixedName, index.toString())).exists()) {
            index++
        }
        return insertBeforeExtension(suffixedName, index.toString())
    }

    private fun insertBeforeExtension(name: String, token: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) {
            "${name.substring(0, dotIndex)}-$token${name.substring(dotIndex)}"
        } else {
            "$name-$token"
        }
    }
}
