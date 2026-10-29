package com.sza.fastmediasorter.data.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.sza.fastmediasorter.domain.model.WearScheduledMessagePayload
import com.sza.fastmediasorter.service.WearDataLayerPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

private const val START_QUALITY = 85
private const val MIN_QUALITY = 45
private const val QUALITY_STEP = 10

// Base64 grows bytes by 4/3 and the title and text share the message; this leaves them room.
private const val IMAGE_BYTE_BUDGET = WearScheduledMessagePayload.MAX_MESSAGE_BYTES * 3 / 4 - 4_000

/**
 * S4248: shrinks the picture a scheduled watch message carries so it fits one Data Layer message.
 *
 * The long edge goes to the size the watch background already uses, and JPEG quality steps down until
 * the bytes fit. Null means the picture cannot be sent - the document is gone, its permission was
 * revoked, or even the lowest quality does not fit - and the caller sends the text alone.
 */
@Singleton
class ScheduledWatchImageEncoder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun encode(uri: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(Uri.parse(uri)) ?: return@withContext null
            try {
                compressToBudget(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: SecurityException) {
            Timber.w(e, "Scheduled watch picture is no longer readable")
            null
        } catch (e: FileNotFoundException) {
            Timber.w(e, "Scheduled watch picture is gone")
            null
        } catch (e: IOException) {
            Timber.w(e, "Scheduled watch picture could not be read")
            null
        }
    }

    private fun decodeScaled(uri: Uri): Bitmap? {
        val sample = sampleSizeFor(uri) ?: return null
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        return decoded?.let(::scaledToEdge)
    }

    /** The power-of-two sample size that keeps the long edge at or above the target, or null when unreadable. */
    private fun sampleSizeFor(uri: Uri): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / (sample * 2) >= WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX) sample *= 2
        return sample
    }

    private fun scaledToEdge(decoded: Bitmap): Bitmap {
        val scale = WearDataLayerPaths.BACKGROUND_IMAGE_EDGE_PX.toFloat() / max(decoded.width, decoded.height)
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun compressToBudget(bitmap: Bitmap): ByteArray? {
        var quality = START_QUALITY
        while (quality >= MIN_QUALITY) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= IMAGE_BYTE_BUDGET) return bytes
            quality -= QUALITY_STEP
        }
        Timber.w("Scheduled watch picture does not fit one message even at quality %d", MIN_QUALITY)
        return null
    }
}
