package com.sza.fastmediasorter.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.min
import timber.log.Timber

/**
 * DynamicBackgroundProcessor
 *
 * Implements edge-pixel line extension for letterbox/pillarbox areas.
 *
 * The entire bitmap (including software blur) is built on Dispatchers.Default.
 * The main thread only atomically swaps the bitmap and makes the view visible —
 * no progressive GPU rendering artifacts.
 */
class DynamicBackgroundProcessor(
    private val backgroundView: ImageView,
    private val coroutineScope: CoroutineScope
) {

    private var processingJob: Job? = null

    companion object {
        /**
         * Blur approximation factor: the bitmap is scaled down by this factor and then
         * scaled back up with bilinear filtering. 8× gives a smooth, blurry look while
         * keeping the software path very cheap.
         */
        private const val BLUR_DOWN_SCALE = 8

        /**
         * Debounce delay (ms) before starting pixel analysis.
         * Set to 0: cancelling the previous job already acts as debounce.
         * An artificial delay caused strips to appear visibly later than the image.
         */
        private const val DEBOUNCE_MS = 0L
    }

    /**
     * Process [drawable] and apply the dynamic background effect to [backgroundView].
     * Call from any thread — all heavy work runs on Dispatchers.Default.
     */
    fun process(
        drawable: Drawable,
        screenWidth: Int,
        screenHeight: Int
    ) {
        processingJob?.cancel()

        processingJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                delay(DEBOUNCE_MS)

                val sourceBitmap = drawableToBitmap(drawable) ?: run {
                    Timber.w("DynamicBg: Could not convert drawable to bitmap")
                    return@launch
                }

                // Build line-extension bitmap fully off-screen, then blur it — all on Default.
                val rawBitmap = buildBackgroundBitmap(sourceBitmap, screenWidth, screenHeight)
                val bgBitmap = blurBitmap(rawBitmap)

                withContext(Dispatchers.Main) {
                    if (backgroundView.context != null) {
                        applyBackground(bgBitmap)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "DynamicBg: Error processing background")
            }
        }
    }

    /**
     * Hide the dynamic background and cancel any in-progress processing.
     */
    fun clear() {
        processingJob?.cancel()
        processingJob = null
        backgroundView.isVisible = false
        backgroundView.setImageDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(null)
        }
    }

    // ---- Private helpers ----

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Timber.e("DynamicBg: OOM creating bitmap for background")
            return null
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun buildBackgroundBitmap(
        source: Bitmap,
        screenWidth: Int,
        screenHeight: Int
    ): Bitmap {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val sw = screenWidth.coerceAtLeast(1)
        val sh = screenHeight.coerceAtLeast(1)

        val scale = min(sw.toFloat() / srcW.toFloat(), sh.toFloat() / srcH.toFloat())
        val displayedW = (srcW * scale).toInt().coerceAtLeast(1)
        val displayedH = (srcH * scale).toInt().coerceAtLeast(1)
        val imgLeft = ((sw - displayedW) / 2).coerceAtLeast(0)
        val imgTop = ((sh - displayedH) / 2).coerceAtLeast(0)

        val outBitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

        val leftEdge  = IntArray(srcH) { y -> source.getPixel(0, y) }
        val rightEdge = IntArray(srcH) { y -> source.getPixel(srcW - 1, y) }
        val topEdge   = IntArray(srcW) { x -> source.getPixel(x, 0) }
        val bottomEdge = IntArray(srcW) { x -> source.getPixel(x, srcH - 1) }

        // Pillarbox bars — full-width horizontal lines, image layer sits on top.
        if (imgLeft > 0) {
            for (vpY in 0 until sh) {
                val localY = (vpY - imgTop).coerceIn(0, displayedH - 1)
                val srcY = floor(localY.toFloat() * srcH / displayedH).toInt().coerceIn(0, srcH - 1)
                paint.color = leftEdge[srcY]
                canvas.drawLine(0f, vpY + 0.5f, sw / 2f, vpY + 0.5f, paint)
                paint.color = rightEdge[srcY]
                canvas.drawLine(sw / 2f, vpY + 0.5f, sw.toFloat(), vpY + 0.5f, paint)
            }
        }

        // Letterbox bars — full-height vertical lines.
        if (imgTop > 0) {
            for (vpX in 0 until sw) {
                val localX = (vpX - imgLeft).coerceIn(0, displayedW - 1)
                val srcX = floor(localX.toFloat() * srcW / displayedW).toInt().coerceIn(0, srcW - 1)
                paint.color = topEdge[srcX]
                canvas.drawLine(vpX + 0.5f, 0f, vpX + 0.5f, sh / 2f, paint)
                paint.color = bottomEdge[srcX]
                canvas.drawLine(vpX + 0.5f, sh / 2f, vpX + 0.5f, sh.toFloat(), paint)
            }
        }

        return outBitmap
    }

    /**
     * Software blur via scale-down / scale-up with bilinear filtering.
     * Runs entirely on the calling thread (Dispatchers.Default) — no GPU involvement.
     * The result looks like a Gaussian blur of radius ≈ [BLUR_DOWN_SCALE] × 4 px.
     */
    private fun blurBitmap(src: Bitmap): Bitmap {
        val sw = src.width
        val sh = src.height
        val smallW = (sw / BLUR_DOWN_SCALE).coerceAtLeast(1)
        val smallH = (sh / BLUR_DOWN_SCALE).coerceAtLeast(1)

        // Scale down with bilinear filtering (FILTER_BITMAP_FLAG is default in createScaledBitmap)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        if (small !== src) src.recycle()

        // Scale back up
        val blurred = Bitmap.createScaledBitmap(small, sw, sh, true)
        if (blurred !== small) small.recycle()

        return blurred
    }

    /**
     * Convenience overload for video first-frame bitmaps.
     */
    fun processFromBitmap(bitmap: Bitmap, screenWidth: Int, screenHeight: Int) {
        val drawable = BitmapDrawable(backgroundView.resources, bitmap)
        process(drawable, screenWidth, screenHeight)
    }

    private fun applyBackground(bgBitmap: Bitmap) {
        val previousBitmap = (backgroundView.drawable as? BitmapDrawable)?.bitmap
        // Apply bitmap and make visible atomically — no GPU effect applied after the fact.
        backgroundView.isVisible = false
        backgroundView.setImageBitmap(bgBitmap)
        previousBitmap?.takeIf { !it.isRecycled }?.recycle()
        // Remove any leftover RenderEffect from a previous session
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(null)
        }
        backgroundView.isVisible = true
        Timber.d("DynamicBg: Background applied")
    }
}
