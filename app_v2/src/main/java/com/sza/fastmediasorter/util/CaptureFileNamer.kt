package com.sza.fastmediasorter.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reference implementation of the CAPTURE-OUTPUT contract name grammar (rules 1, 3 and 5):
 * `<prefix>_<yyMMdd>_<HHmmss>[ (<n>)].<ext>`, formatted with an invariant locale so the digits stay
 * ASCII in every interface language. [allocate] keeps the ordinal for same-second captures inside
 * this process; [freeNameIn] / [nextFreeName] extend it to what already exists in the destination.
 */
class CaptureFileNamer {

    enum class CaptureKind(val prefix: String) {
        PHOTO("photo"),
        SCREENSHOT("screenshot"),
        AUDIO("audio"),
        VIDEO("video"),
        SCREEN_VIDEO("screen_video"),
        VIDEO_FRAME("video_frame"),
        OCR_TEXT("ocr_text"),
        TRANSLATION("translation"),
        BROADCAST("broadcast"),
    }

    private data class AllocationState(val timestamp: String, val ordinal: Int)

    private val allocationStates = mutableMapOf<CaptureKind, AllocationState>()

    @Synchronized
    fun allocate(
        kind: CaptureKind,
        extension: String,
        timestampMillis: Long = System.currentTimeMillis(),
    ): String {
        val timestamp = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).format(Date(timestampMillis))
        val previousState = allocationStates[kind]
        val ordinal = if (previousState?.timestamp == timestamp) {
            previousState.ordinal + FIRST_ORDINAL
        } else {
            FIRST_ORDINAL
        }
        allocationStates[kind] = AllocationState(timestamp, ordinal)
        val suffix = if (ordinal == FIRST_ORDINAL) "" else " ($ordinal)"
        val normalizedExtension = extension.takeIf { it.startsWith(EXTENSION_SEPARATOR) }
            ?: "$EXTENSION_SEPARATOR$extension"
        val fileName = "${kind.prefix}_$timestamp$suffix$normalizedExtension"
        return fileName
    }

    companion object {
        const val DATE_TIME_PATTERN = "yyMMdd_HHmmss"
        private const val EXTENSION_SEPARATOR = "."
        private const val FIRST_ORDINAL = 1
        private const val SECOND_ORDINAL = 2
        private val ORDINAL_SUFFIX = Regex(""" \((\d+)\)$""")

        val shared = CaptureFileNamer()

        /**
         * Returns [fileName] when [isTaken] says it is free, otherwise `<stem> (n)<ext>` with the
         * smallest free n from 2 upwards. A stem ending in ` (k)` whose un-suffixed sibling is also
         * taken is an ordinal already (from [allocate]) and continues from k + 1; a typed name such as
         * `Report (2019)` with no such sibling keeps its parenthesis and gets its own ordinal.
         */
        fun nextFreeName(fileName: String, isTaken: (String) -> Boolean): String {
            if (!isTaken(fileName)) return fileName
            val dotIndex = fileName.lastIndexOf(EXTENSION_SEPARATOR)
            val stem = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""
            val existingOrdinal = ORDINAL_SUFFIX.find(stem)
                ?.takeIf { isTaken(stem.substring(0, it.range.first) + extension) }
            val baseStem = existingOrdinal?.let { stem.substring(0, it.range.first) } ?: stem
            var ordinal = existingOrdinal?.groupValues?.get(1)?.toIntOrNull()
                ?.let { it + FIRST_ORDINAL }
                ?: SECOND_ORDINAL
            var candidate = "$baseStem ($ordinal)$extension"
            while (isTaken(candidate)) {
                ordinal++
                candidate = "$baseStem ($ordinal)$extension"
            }
            return candidate
        }

        /** [nextFreeName] against the files already present in [dir]. */
        fun freeNameIn(dir: File, fileName: String): String =
            nextFreeName(fileName) { File(dir, it).exists() }
    }
}
