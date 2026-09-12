package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** Longest edge of the produced picture. A watch cell never draws more than this. */
private const val MAX_EDGE_PX = 128

/**
 * Ceiling on the Base64 the page carries, per item. The page holds every item's picture, so a
 * folder of twenty files must stay in the low hundreds of kilobytes rather than the megabytes a
 * per-item ceiling of "whatever compressed to" would allow.
 *
 * S1860: lowered from 16 KB after the transport refused a real page. Gson writes the envelope's
 * `ByteArray` as a JSON array of numbers, so every payload byte costs about four on the wire, and
 * GMS caps one data item at 100 KB - a ceiling that let one picture eat two thirds of the page.
 * Spreading the same allowance over more, smaller pictures beats shipping one large one.
 */
internal const val MAX_ENCODED_CHARS = 4 * 1024

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
    @ApplicationContext private val context: Context,
    private val thumbnailCacheRepository: ThumbnailCacheRepository
) {

    suspend operator fun invoke(file: MediaFile): String? = withContext(Dispatchers.IO) {
        val cachedFile = thumbnailCacheRepository.getCachedThumbnail(file.path)
        if (cachedFile != null && cachedFile.exists()) {
            val cachedBitmap = BitmapFactory.decodeFile(cachedFile.absolutePath)
            if (cachedBitmap != null) {
                return@withContext encodeWithinCeiling(cachedBitmap)
            }
        }

        val decoded = decodePreview(file)
        if (decoded != null) {
            saveToCache(file.path, decoded)
            encodeWithinCeiling(decoded)
        } else {
            null
        }
    }

    private suspend fun saveToCache(filePath: String, bitmap: Bitmap) {
        try {
            val tempFile = File(context.cacheDir, "watch_thumb_${UUID.randomUUID()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, FIRST_QUALITY, out)
            }
            thumbnailCacheRepository.saveThumbnail(filePath, tempFile)
            tempFile.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Failed to save watch thumbnail to ThumbnailCacheRepository for %s", filePath)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Failed to save watch thumbnail to ThumbnailCacheRepository for %s", filePath)
        }
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
        readStream(file)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return readStream(file)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * S1950: the MediaStore uri is preferred over the path, exactly as the video and audio branches
     * already do it. Since Android 11 a file another app owns is readable through the resolver and
     * not always through its path, and a picture that fails to decode ships to the watch as no
     * picture at all - indistinguishable from a phone that has none.
     */
    private fun readStream(file: MediaFile): InputStream? = try {
        file.contentUri?.let { context.contentResolver.openInputStream(Uri.parse(it)) }
            ?: File(file.path).takeIf { it.canRead() }?.inputStream()
    } catch (e: IOException) {
        declinedStream(file, e)
    } catch (e: SecurityException) {
        declinedStream(file, e)
    }

    private fun declinedStream(file: MediaFile, error: Exception): InputStream? {
        Timber.w(error, "No readable stream for %s", file.name)
        return null
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
     *
     * S1860: the catch is deliberately as wide as the platform's own contract. `setDataSource`
     * reports a file it cannot open by throwing a BARE `RuntimeException`
     * ("setDataSource failed: status = 0xFFFFFFEA"), which the narrower arms this replaced did not
     * catch - so on a real pair one unreadable track killed the app in the middle of building a
     * watch page, and the watch, waiting for a page nobody would ever send, told the user the phone
     * was out of reach. Every outcome here is the same "no picture for this item", so widening the
     * arm loses no information the caller could have acted on.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun readMetadata(file: MediaFile, read: (MediaMetadataRetriever) -> Bitmap?): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            file.contentUri?.let { retriever.setDataSource(context, Uri.parse(it)) }
                ?: retriever.setDataSource(file.path)
            read(retriever)
        } catch (e: RuntimeException) {
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
