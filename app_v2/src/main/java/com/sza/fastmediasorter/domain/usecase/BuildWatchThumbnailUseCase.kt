package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/** Longest edge of the produced picture. A watch cell never draws more than this. */
private const val MAX_EDGE_PX = 128

/**
 * Ceiling on the Base64 the page carries, per item. The page holds every item's picture, so a
 * folder of twenty files must stay in the low hundreds of kilobytes rather than the megabytes a
 * per-item ceiling of "whatever compressed to" would allow.
 */
internal const val MAX_ENCODED_CHARS = 16 * 1024

private const val FIRST_QUALITY = 70
private const val LOWEST_QUALITY = 40
private const val QUALITY_STEP = 15
private const val FIRST_FRAME_US = 0L

/**
 * Prepares the picture the phone ships to the watch inside a folder page.
 *
 * The watch cannot reach a phone folder itself, so the phone produces the thumbnail; the answer is
 * deliberately nullable, because "this file carries no preview" is an ordinary outcome the watch
 * renders as a type icon rather than an error.
 */
class BuildWatchThumbnailUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend operator fun invoke(file: MediaFile): String? = withContext(Dispatchers.IO) {
        decodePreview(file)?.let { encodeWithinCeiling(it) }
    }

    private fun decodePreview(file: MediaFile): Bitmap? = when {
        file.isDirectory -> null
        file.type == MediaType.IMAGE || file.type == MediaType.GIF -> decodeImage(file)
        file.type == MediaType.VIDEO -> decodeVideoFrame(file)
        file.type == MediaType.AUDIO -> decodeAlbumCover(file)
        else -> null
    }

    /**
     * Bounds are read first so the full picture never enters memory: a phone photo is tens of
     * megapixels and the watch needs a 128 px square of it.
     */
    private fun decodeImage(file: MediaFile): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun decodeVideoFrame(file: MediaFile): Bitmap? =
        readMetadata(file) { it.getFrameAtTime(FIRST_FRAME_US) }

    private fun decodeAlbumCover(file: MediaFile): Bitmap? =
        readMetadata(file) { retriever ->
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }

    /**
     * A file the retriever refuses is a normal outcome - a codec the device lacks, a track with no
     * cover - so the failure answers null and the page simply carries no picture for that item.
     */
    private fun readMetadata(file: MediaFile, read: (MediaMetadataRetriever) -> Bitmap?): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            file.contentUri?.let { retriever.setDataSource(context, Uri.parse(it)) }
                ?: retriever.setDataSource(file.path)
            read(retriever)
        } catch (e: IllegalArgumentException) {
            declined(file, e)
        } catch (e: IllegalStateException) {
            declined(file, e)
        } catch (e: IOException) {
            declined(file, e)
        } finally {
            retriever.release()
        }
    }

    private fun declined(file: MediaFile, error: Exception): Bitmap? {
        Timber.w(error, "No frame or cover available for %s", file.name)
        return null
    }

    /**
     * Quality is stepped down rather than accepted once: the ceiling belongs to the page, so a
     * picture that will not fit is dropped instead of made the page's problem.
     */
    private fun encodeWithinCeiling(bitmap: Bitmap): String? {
        val scaled = downscale(bitmap)
        var quality = FIRST_QUALITY
        while (quality >= LOWEST_QUALITY) {
            val encoded = compressToBase64(scaled, quality)
            if (encoded.length <= MAX_ENCODED_CHARS) {
                return encoded
            }
            quality -= QUALITY_STEP
        }
        return null
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        if (longestEdge <= MAX_EDGE_PX) {
            return bitmap
        }
        val ratio = MAX_EDGE_PX.toFloat() / longestEdge
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun compressToBase64(bitmap: Bitmap, quality: Int): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (maxOf(width, height) / sampleSize > MAX_EDGE_PX) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
