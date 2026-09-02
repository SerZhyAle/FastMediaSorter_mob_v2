package com.sza.fastmediasorter.wear.data.thumbnail

import android.graphics.Bitmap
import android.media.ExifInterface
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private const val COPY_CHUNK_BYTES = 8 * 1024
private const val END_OF_STREAM = -1

/**
 * Pulls the preview a camera already stored inside a photo, reading only the head of the file.
 *
 * The stream is closed the moment the budgeted number of bytes is in hand, so a folder of photos
 * costs kilobytes rather than the megabytes the files themselves weigh. A head that carries no
 * preview yields null; the partial bytes are never decoded as an image of their own, because a
 * truncated file is not a smaller picture, it is an invalid one.
 */
class EmbeddedPreviewReader @Inject constructor() {

    suspend fun read(stream: InputStream): Bitmap? = withContext(Dispatchers.IO) {
        val head = readHead(stream)
        val preview = if (head.isEmpty()) null else extractThumbnail(head)
        preview
    }

    /**
     * Internal rather than private so the byte ceiling can be asserted directly. The decode below
     * needs an Android runtime the watch module has no unit-test harness for, but the number of
     * bytes pulled off the wire is the property worth proving and it is pure Kotlin.
     */
    internal fun readHead(stream: InputStream): ByteArray = stream.use { source ->
        val head = ByteArray(WearThumbnailBudget.MAX_HEAD_READ_BYTES)
        val chunk = ByteArray(COPY_CHUNK_BYTES)
        var filled = 0
        while (filled < head.size) {
            val wanted = minOf(chunk.size, head.size - filled)
            val read = source.read(chunk, 0, wanted)
            if (read == END_OF_STREAM) break
            chunk.copyInto(head, filled, 0, read)
            filled += read
        }
        head.copyOf(filled)
    }

    private fun extractThumbnail(head: ByteArray): Bitmap? =
        try {
            val exif = ExifInterface(ByteArrayInputStream(head))
            if (exif.hasThumbnail()) exif.thumbnailBitmap else null
        } catch (e: IOException) {
            // Callers filter by type before reaching here, so an unreadable head means a file that
            // claims a format it does not hold. The cell falls back to its type icon.
            Timber.w(e, "Embedded preview head unreadable")
            null
        }
}
