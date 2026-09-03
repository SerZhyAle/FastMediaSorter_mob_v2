package com.sza.fastmediasorter.wear.data.recorder

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2161: publishes a finished voice recording from the app's private storage into
 * [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI] so it appears as an ordinary audio file.
 *
 * Publication requires API 29+ (Scoped Storage without write permission). Below API 29,
 * the publisher returns null and leaves the note in private storage (ADR-4).
 */
@Singleton
class VoiceNotePublisher(
    private val contentResolver: ContentResolver,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        contentResolver = context.contentResolver,
        sdkIntProvider = { Build.VERSION.SDK_INT }
    )

    /**
     * Publishes [file] into shared audio storage. Returns the published [Uri] on success,
     * or null if refused (pre-Q) or on error.
     */
    fun publish(file: File): Uri? {
        Timber.d("S2161: voice note publication requested")
        val sdkInt = sdkIntProvider()
        if (!canPublish(sdkInt, file)) return null
        val uri = insertPendingRow(file, sdkInt)
        return when {
            uri == null -> null
            copyBytesInto(uri, file) -> commitPending(uri)
            else -> discardPending(uri)
        }
    }

    private fun canPublish(sdkInt: Int, file: File): Boolean {
        if (sdkInt < Build.VERSION_CODES.Q) {
            Timber.i("VoiceNotePublisher: skipping MediaStore publish below API 29 (sdk=%d)", sdkInt)
            return false
        }
        val usable = file.exists() && file.length() > 0L
        if (!usable) {
            Timber.w("VoiceNotePublisher: refusing to publish non-existent or empty file %s", file.name)
        }
        return usable
    }

    private fun insertPendingRow(file: File, sdkInt: Int): Uri? {
        val initialValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeOf(file.name) ?: DEFAULT_MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, recordingsRelativePath(sdkInt))
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = try {
            contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, initialValues)
        } catch (e: SecurityException) {
            Timber.w(e, "VoiceNotePublisher: insert refused by security policy for %s", file.name)
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "VoiceNotePublisher: insert failed with invalid arguments for %s", file.name)
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "VoiceNotePublisher: insert failed with illegal state for %s", file.name)
            null
        }
        if (uri == null) {
            Timber.w("VoiceNotePublisher: insert returned null uri for %s", file.name)
        }
        return uri
    }

    private fun copyBytesInto(uri: Uri, file: File): Boolean =
        try {
            val sink = contentResolver.openOutputStream(uri)
                ?: throw IOException("openOutputStream returned null for $uri")
            sink.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                out.flush()
            }
            true
        } catch (e: FileNotFoundException) {
            Timber.w(e, "VoiceNotePublisher: target uri %s or source file %s not found", uri, file.name)
            false
        } catch (e: IOException) {
            Timber.w(e, "VoiceNotePublisher: io error copying bytes to %s", uri)
            false
        } catch (e: SecurityException) {
            Timber.w(e, "VoiceNotePublisher: security error writing to %s", uri)
            false
        }

    private fun commitPending(uri: Uri): Uri? {
        val commitValues = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        return try {
            val updated = contentResolver.update(uri, commitValues, null, null)
            if (updated <= 0) {
                Timber.w("VoiceNotePublisher: IS_PENDING update returned 0 for %s", uri)
            }
            uri
        } catch (e: SecurityException) {
            Timber.w(e, "VoiceNotePublisher: commit IS_PENDING failed for %s", uri)
            discardPending(uri)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "VoiceNotePublisher: commit IS_PENDING failed for %s", uri)
            discardPending(uri)
        }
    }

    /** Drops the half-written row and reports failure, so a refused publish leaves no pending entry. */
    private fun discardPending(uri: Uri): Uri? {
        cleanupPending(uri)
        return null
    }

    private fun cleanupPending(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            Timber.w(e, "VoiceNotePublisher: cleanup delete failed for %s", uri)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "VoiceNotePublisher: cleanup delete failed for %s", uri)
        }
    }

    companion object {
        const val DIRECTORY_RECORDINGS_CANONICAL = "Recordings"
        private const val DEFAULT_MIME_TYPE = "audio/mp4"

        fun recordingsDirectoryName(sdkInt: Int): String {
            if (sdkInt < Build.VERSION_CODES.S) return DIRECTORY_RECORDINGS_CANONICAL
            // DIRECTORY_RECORDINGS resolves against the device framework, so an image whose
            // android.os.Environment predates API 31 fails to link it rather than returning null.
            return try {
                Environment.DIRECTORY_RECORDINGS ?: DIRECTORY_RECORDINGS_CANONICAL
            } catch (e: LinkageError) {
                Timber.w(e, "VoiceNotePublisher: DIRECTORY_RECORDINGS unavailable, using canonical name")
                DIRECTORY_RECORDINGS_CANONICAL
            }
        }

        fun recordingsRelativePath(sdkInt: Int): String =
            "${recordingsDirectoryName(sdkInt)}/"

        // S2443: the module resolves an extension in one place. The audio-context entry "mp4" ->
        // "audio/mp4" that used to live here did not move with it: the platform table answers "mp4"
        // before any fallback is consulted, and VoiceNoteFileFactory writes only ".m4a".
        fun mimeTypeOf(displayName: String): String? = MediaMimeTypes.fromFileName(displayName)
    }
}
