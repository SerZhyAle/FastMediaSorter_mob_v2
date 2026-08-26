package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Ignored by the PNG encoder, which is lossless, but the platform signature demands a value. */
private const val PNG_QUALITY = 100

private const val SAMPLE_STEP = 2

/**
 * Ceiling on the transient decode, in pixels - about 33 MB at ARGB_8888.
 *
 * Sampling alone is driven by the short edge, because that is the edge the square is cut from, so an
 * extreme aspect ratio (a panorama, a scanned strip) would pass every sampling step and still decode
 * whole. The crop throws all of it away except one square, and an OutOfMemoryError on the way there
 * is not a failure this returns - it is a crash.
 */
private const val MAX_DECODED_PIXELS = 8_000_000L

/**
 * S2000: reduces a picked image to the one frame the watch is willing to draw.
 *
 * The work sits on the phone because the size was named by the owner and the watch would otherwise
 * pay for it either on receipt or on every drawn frame (strategic ADR-2). It is a use case taking a
 * [Uri] and answering a [File] rather than logic inside a composable, because that conversion is one
 * of the two things the ticket must prove without launching the UI (strategic section 11.8).
 */
class PrepareWearBackgroundImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * A failed [Result] rather than a thrown exception: the caller renders the cause as text, and a
     * picture that will not convert is an ordinary outcome of letting the user pick any file.
     */
    suspend operator fun invoke(source: Uri): Result<File> = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, WearDataLayerPaths.BACKGROUND_IMAGE_FILE_NAME)
        try {
            writeFrame(squareFrameOf(source), target)
            Result.success(target)
        } catch (e: IOException) {
            discard(target)
            Result.failure(e)
        } catch (e: SecurityException) {
            discard(target)
            Result.failure(e)
        }
    }

    private fun squareFrameOf(source: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = openStream(source).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Not a decodable image: $source")
        return scaleToCanonicalEdge(centreCrop(decoded))
    }

    private fun openStream(source: Uri): InputStream =
        context.contentResolver.openInputStream(source)
            ?: throw IOException("No readable stream for $source")

    /**
     * Sampling stops at the last step that still leaves the short edge at or above the canonical
     * one, so the crop that follows never has to enlarge what the decoder threw away - unless the
     * picture is so lopsided that keeping the short edge would blow [MAX_DECODED_PIXELS], in which
     * case the ceiling wins and a pathological source is slightly enlarged instead of crashing.
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (shortEdgeStillSpare(width, height, sampleSize) || tooManyPixels(width, height, sampleSize)) {
            sampleSize *= SAMPLE_STEP
        }
        return sampleSize
    }

    private fun shortEdgeStillSpare(width: Int, height: Int, sampleSize: Int): Boolean =
        minOf(width, height) / (sampleSize * SAMPLE_STEP) >=
            WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX

    private fun tooManyPixels(width: Int, height: Int, sampleSize: Int): Boolean =
        (width / sampleSize).toLong() * (height / sampleSize) > MAX_DECODED_PIXELS

    private fun centreCrop(bitmap: Bitmap): Bitmap {
        val edge = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - edge) / 2
        val top = (bitmap.height - edge) / 2
        return Bitmap.createBitmap(bitmap, left, top, edge, edge)
    }

    private fun scaleToCanonicalEdge(square: Bitmap): Bitmap = Bitmap.createScaledBitmap(
        square,
        WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX,
        WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX,
        true
    )

    /**
     * A refused compression answers false instead of throwing, so it is turned into the same failure
     * as an unwritable file - otherwise the caller would receive a truncated frame reported as success.
     */
    private fun writeFrame(frame: Bitmap, target: File) {
        FileOutputStream(target).use { out ->
            if (!frame.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) {
                throw IOException("Cannot encode the background frame into ${target.name}")
            }
        }
    }

    /** Half a frame on disk would be sent as if it were whole, so a failed run leaves nothing. */
    private fun discard(target: File) {
        target.delete()
    }
}
