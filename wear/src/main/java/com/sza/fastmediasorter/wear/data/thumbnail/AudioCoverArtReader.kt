package com.sza.fastmediasorter.wear.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.sza.fastmediasorter.wear.util.WearThumbnailBudget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Extracts embedded album artwork from an audio file via [MediaMetadataRetriever].
 *
 * MP3 stores art in ID3 APIC frames, FLAC in PICTURE metadata blocks, and AAC/M4A in iTunes-style
 * `covr` atoms. [MediaMetadataRetriever.embeddedPicture] reads all three with a single call,
 * which is why this class does not parse any container itself.
 *
 * The bitmap is downscaled to [WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX] immediately: a phone
 * photo used as cover art can be several megapixels, and the watch cell never draws more than the
 * budget allows.
 */
class AudioCoverArtReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @return the embedded picture downscaled to the watch budget, or null when the file carries
     *   none or when the retriever refuses the codec.
     */
    suspend fun read(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { raw ->
                    downscale(raw)
                }
            }
        } catch (e: CancellationException) {
            // A scrolled-away cell cancels this read; the broad arm below is a supertype of it, so
            // without this the coroutine would be silently un-cancellable (S1363/S1889/S1910).
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            // setDataSource throws a bare RuntimeException for codecs the device lacks or files
            // it cannot open, matching the phone-side pattern in BuildWatchThumbnailUseCase.
            Timber.w(e, "No cover art available for %s", uri)
            null
        } catch (e: IOException) {
            Timber.w(e, "No cover art available for %s", uri)
            null
        } finally {
            retriever.release()
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val edge = WearThumbnailBudget.MAX_THUMBNAIL_EDGE_PX
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val ratio = edge.toFloat() / longest
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
