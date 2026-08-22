package com.sza.fastmediasorter.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Allocates source-specific visible names while preserving an in-process collision sequence. */
class CaptureFileNamer {

    enum class CaptureKind(val prefix: String) {
        PHOTO("photo"),
        SCREENSHOT("screenshot"),
        AUDIO("audio"),
        VIDEO("video"),
        SCREEN_VIDEO("screen_video"),
        VIDEO_FRAME("video_frame"),
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

        val shared = CaptureFileNamer()
    }
}
