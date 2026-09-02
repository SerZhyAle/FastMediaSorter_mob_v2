package com.sza.fastmediasorter.wear.data.db

import android.media.MediaMetadataRetriever
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S2356: reads one recording's duration back off disk.
 *
 * A seam rather than a direct call, so [VoiceNoteIndexRebuilder] stays testable on the JVM:
 * `MediaMetadataRetriever` is an android.jar stub under `wear/src/test` and throws on first use,
 * and the rebuild is exactly the code ADR-4 requires a test to hold against the entity.
 */
fun interface VoiceNoteDurationReader {

    /** Zero for a file that carries no readable duration - S1862 keeps the note either way. */
    fun durationMillisOf(file: File): Long
}

/**
 * The shipped reader. Every failure yields zero rather than propagating: strategic 5.1 registers a
 * file that cannot be read as a recording instead of dropping it, because speech cannot be recorded
 * a second time.
 */
class MediaMetadataVoiceNoteDurationReader : VoiceNoteDurationReader {

    override fun durationMillisOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Voice note %s is not readable as media", file.name)
            0L
        } catch (e: IllegalStateException) {
            Timber.w(e, "Voice note %s yielded no duration metadata", file.name)
            0L
        } finally {
            release(retriever, file)
        }
    }

    // release() is documented to throw since API 29, and a failure closing the retriever must not
    // discard the duration already read.
    private fun release(retriever: MediaMetadataRetriever, file: File) {
        try {
            retriever.release()
        } catch (e: IOException) {
            Timber.w(e, "Releasing the metadata retriever for %s failed", file.name)
        }
    }
}
