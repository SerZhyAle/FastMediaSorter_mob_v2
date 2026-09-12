package com.sza.fastmediasorter.wear.data.recorder

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Section 5.4: the phone plays `.m4a` from all three of its own recording paths, so the watch writes it. */
private const val VOICE_NOTE_EXTENSION = "m4a"

/** Mirrors `CaptureFileNamer.DATE_TIME_PATTERN` in app_v2 - the two modules share no artifact. */
private const val DATE_TIME_PATTERN = "yyMMdd_HHmmss"

/** Mirrors `CaptureFileNamer.CaptureKind.AUDIO.prefix`, so a watch note reads like a phone one. */
private const val VOICE_NOTE_PREFIX = "audio"

/** The first collision suffix; the first file of a second carries none at all. */
private const val FIRST_COLLISION_ORDINAL = 2

/** A second of speech cannot outlast a second of clock, so a same-second collision cannot run long. */
private const val MAX_COLLISION_ORDINAL = 99

/**
 * S1862 / S2161: allocates the target file for one recording, inside the app's private storage.
 *
 * S2161: The private file is the working copy a recording is captured into. Publication follows a
 * successful stop, and appearing in both the note list and the audio collection is intended - the
 * two lists answer different questions.
 *
 * Collisions are resolved against the disk rather than against an in-process counter (which is what
 * `CaptureFileNamer` does on the phone): the service that calls this can be killed and restarted
 * between two notes of the same second, and a counter that resets would overwrite the first one.
 */
@Singleton
class VoiceNoteFileFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Created on demand: nothing outside this class is allowed to assume the directory exists. */
    fun directory(): File = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /**
     * Returns a file that does not exist yet. The suffix is `_2`, `_3`.. rather than the phone's
     * ` (2)`: the name travels as part of a Data Layer channel path, and a space there is a needless
     * escaping question for a name nobody types by hand.
     */
    fun newFile(timestampMillis: Long = System.currentTimeMillis()): File {
        val directory = directory()
        val timestamp = SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).format(Date(timestampMillis))
        val plain = File(directory, "${VOICE_NOTE_PREFIX}_$timestamp.$VOICE_NOTE_EXTENSION")
        if (!plain.exists()) {
            return plain
        }
        return firstFreeVariant(directory, timestamp)
    }

    private fun firstFreeVariant(directory: File, timestamp: String): File {
        var candidate: File? = null
        var ordinal = FIRST_COLLISION_ORDINAL
        while (candidate == null && ordinal <= MAX_COLLISION_ORDINAL) {
            val next = File(directory, "${VOICE_NOTE_PREFIX}_${timestamp}_$ordinal.$VOICE_NOTE_EXTENSION")
            if (!next.exists()) {
                candidate = next
            }
            ordinal++
        }
        // Past the ceiling the millisecond disambiguates what the second could not.
        return candidate
            ?: File(directory, "${VOICE_NOTE_PREFIX}_${timestamp}_${System.nanoTime()}.$VOICE_NOTE_EXTENSION")
    }

    companion object {
        private const val DIRECTORY_NAME = "voice_notes"
    }
}
