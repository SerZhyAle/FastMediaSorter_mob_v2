package com.sza.fastmediasorter.wear.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileCollection
import com.sza.fastmediasorter.wear.domain.files.WearWatchFileTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/**
 * Turns a file the watch only borrowed into one of its own, written into shared storage for good.
 *
 * Everything the watch holds of a phone file or a network file today sits in an evictable cache, so
 * a copy left there is gone the next time the cache is trimmed and shows up in no category list
 * meanwhile. Publishing a MediaStore row is what makes the copy an ordinary file of the watch, and
 * the voice-note publisher is the precedent this follows: insert pending, stream, commit.
 *
 * The length check is the part that is not cosmetic. A move deletes the source against this class's
 * answer, so a stream that ended early must never read as a stored copy - the row is committed only
 * when as many bytes arrived as the source held, and a short write drops the pending row instead.
 */
class WearWatchFilePublisher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** What a publish attempt ended as; nothing in between, because a partial row is never committed. */
    sealed interface Result {

        /**
         * [finalName] is read back from the committed row rather than echoed from the request: the
         * store resolves a colliding display name by suffixing it, and the owner has to be told the
         * name the file actually carries (S1863).
         */
        data class Published(val uri: Uri, val finalName: String) : Result

        data object Failed : Result
    }

    /**
     * Whether this watch can be published to at all.
     *
     * Asked by the capability policy before the operation is offered, so a watch below API 29
     * withholds "copy to watch" instead of drawing an entry whose only possible answer is a refusal -
     * the rule S2004 ADR-4 already applies to every action the device cannot perform.
     */
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Free space on the volume the copies land on.
     *
     * Measured through the app's own directory on that volume: a watch has one emulated shared
     * volume, and this path needs no permission to stat, where the shared root's own file handle is
     * deprecated from the API this class already requires.
     */
    fun freeBytes(): Long = context.getExternalFilesDir(null)?.usableSpace ?: 0L

    fun publish(
        source: File,
        displayName: String,
        mimeType: String,
        collection: WearWatchFileCollection
    ): Result {
        val uri = insertPendingRow(source, displayName, mimeType, collection)
        return when {
            uri == null -> Result.Failed
            copyBytesInto(uri, source) && commitPending(uri) ->
                Result.Published(uri, finalNameOf(uri, displayName))
            else -> discardPending(uri)
        }
    }

    /**
     * Below API 29 a row cannot be inserted without a storage permission the watch never asks for, so
     * the class declines here and the operation is withheld upstream rather than offered and refused.
     */
    private fun canPublish(source: File): Boolean {
        if (!isAvailable()) {
            Timber.i(
                "WearWatchFilePublisher: shared-storage publish needs API 29 (sdk=%d)",
                Build.VERSION.SDK_INT
            )
            return false
        }
        val usable = source.exists() && source.length() > 0L
        if (!usable) {
            Timber.w("WearWatchFilePublisher: refusing to publish missing or empty %s", source.name)
        }
        return usable
    }

    private fun insertPendingRow(
        source: File,
        displayName: String,
        mimeType: String,
        collection: WearWatchFileCollection
    ): Uri? {
        if (!canPublish(source)) return null
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, WearWatchFileTarget.relativePathOf(collection))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return insertRow(contentUriOf(collection), values, displayName)
    }

    private fun insertRow(collectionUri: Uri, values: ContentValues, displayName: String): Uri? = try {
        context.contentResolver.insert(collectionUri, values)
    } catch (e: SecurityException) {
        Timber.w(e, "WearWatchFilePublisher: insert refused by security policy for %s", displayName)
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "WearWatchFilePublisher: insert failed with invalid arguments for %s", displayName)
        null
    } catch (e: IllegalStateException) {
        Timber.w(e, "WearWatchFilePublisher: insert failed with illegal state for %s", displayName)
        null
    }

    private fun copyBytesInto(uri: Uri, source: File): Boolean = try {
        val expected = source.length()
        val sink = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("openOutputStream returned null for $uri")
        val written = sink.use { out ->
            val copied = source.inputStream().use { input -> input.copyTo(out) }
            out.flush()
            copied
        }
        val complete = written == expected
        if (!complete) {
            Timber.w(
                "WearWatchFilePublisher: wrote %d of %d bytes for %s",
                written,
                expected,
                source.name
            )
        }
        complete
    } catch (e: FileNotFoundException) {
        Timber.w(e, "WearWatchFilePublisher: target %s or source %s not found", uri, source.name)
        false
    } catch (e: IOException) {
        Timber.w(e, "WearWatchFilePublisher: io error copying bytes to %s", uri)
        false
    } catch (e: SecurityException) {
        Timber.w(e, "WearWatchFilePublisher: security error writing to %s", uri)
        false
    }

    private fun commitPending(uri: Uri): Boolean {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        return try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: SecurityException) {
            Timber.w(e, "WearWatchFilePublisher: could not commit %s", uri)
            false
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "WearWatchFilePublisher: could not commit %s", uri)
            false
        }
    }

    /** A pending row nothing committed stays invisible, but it still holds its bytes - so it goes. */
    private fun discardPending(uri: Uri): Result {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            Timber.w(e, "WearWatchFilePublisher: cleanup delete failed for %s", uri)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "WearWatchFilePublisher: cleanup delete failed for %s", uri)
        }
        return Result.Failed
    }

    /** The requested name stands in when the row cannot be read back; it is right unless it collided. */
    private fun finalNameOf(uri: Uri, requested: String): String {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: requested
        } catch (e: SecurityException) {
            Timber.w(e, "WearWatchFilePublisher: could not read back the name of %s", uri)
            requested
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "WearWatchFilePublisher: could not read back the name of %s", uri)
            requested
        }
    }

    private fun contentUriOf(collection: WearWatchFileCollection): Uri = when (collection) {
        WearWatchFileCollection.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        WearWatchFileCollection.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        WearWatchFileCollection.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}
