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
 * The main thread only atomically swaps the bitmap and makes the view visible -
 * no progressive GPU rendering artifacts.
 */
class DynamicBackgroundProcessor(
    private val backgroundView: ImageView,
    private val coroutineScope: CoroutineScope
) {

    private var processingJob: Job? = null

    companion object {
        /**
         * The relative fraction size used to calculate the blur radius for the 1D color arrays.
         * Value 0.0006f gives a minimal, subtle blend just to eliminate harsh artifacts.
         */
        private const val SMOOTH_RADIUS_FACTOR = 0.0006f

        /**
         * Debounce delay (ms) before starting pixel analysis.
         * Set to 0: cancelling the previous job already acts as debounce.
         * An artificial delay caused strips to appear visibly later than the image.
         */
        private const val DEBOUNCE_MS = 0L
    }

    /**
     * Process [drawable] and apply the dynamic background effect to [backgroundView].
     * [screenWidth]/[screenHeight] are the dimensions of the ImageView that displays the media
     * (NOT the full screen). Pass the view's laid-out pixel size so that pillarbox/letterbox
     * offsets are computed against the actual display area.
     * Call from any thread - all heavy work runs on Dispatchers.Default.
     */
    fun process(
        drawable: Drawable,
        screenWidth: Int,
        screenHeight: Int
    ) {
        processingJob?.cancel()

        // Resolve dimensions: prefer the caller-supplied view size; if not yet laid out,
        // fall back to backgroundView's own dimensions (it covers the same media area).
        val resolvedW = screenWidth.takeIf { it > 0 }
            ?: backgroundView.width.takeIf { it > 0 }
            ?: return
        val resolvedH = screenHeight.takeIf { it > 0 }
            ?: backgroundView.height.takeIf { it > 0 }
            ?: return

        processingJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                delay(DEBOUNCE_MS)

                val sourceBitmap = drawableToBitmap(drawable) ?: run {
                    Timber.w("DynamicBg: Could not convert drawable to bitmap")
                    return@launch
                }

                // Build line-extension bitmap fully off-screen - all on Default.
                val bgBitmap = buildBackgroundBitmap(sourceBitmap, resolvedW, resolvedH)

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

        val rawLeftEdge  = IntArray(srcH) { y -> source.getPixel(0, y) }
        val rawRightEdge = IntArray(srcH) { y -> source.getPixel(srcW - 1, y) }
        val rawTopEdge   = IntArray(srcW) { x -> source.getPixel(x, 0) }
        val rawBottomEdge = IntArray(srcW) { x -> source.getPixel(x, srcH - 1) }

        // Calculate an integer radius using the factor (radius must be an integer for array traversal).
        // For a minimal effect that just blends adjacent edges, we ensure radius is at least 1,
        // otherwise a radius of 0 means absolutely no blending.
        val radiusH = Math.round(srcH * SMOOTH_RADIUS_FACTOR).coerceAtLeast(1)
        val radiusW = Math.round(srcW * SMOOTH_RADIUS_FACTOR).coerceAtLeast(1)

        val leftEdge = smoothColorArray(rawLeftEdge, radiusH)
        val rightEdge = smoothColorArray(rawRightEdge, radiusH)
        val topEdge = smoothColorArray(rawTopEdge, radiusW)
        val bottomEdge = smoothColorArray(rawBottomEdge, radiusW)

        // Pillarbox bars (space left/right of image) - one horizontal line per row,
        // colour taken from the nearest left/right edge pixel of the source.
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

        // Letterbox bars (space above/below image) - one vertical line per column,
        // colour taken from the top/bottom edge pixel of that source column.
        // This produces horizontal colour variation that matches the image edge -
        // the bar looks like a natural extension of the top/bottom row of the photo.
        if (imgTop > 0) {
            val imgBottom = imgTop + displayedH
            for (vpX in 0 until sw) {
                val localX = (vpX - imgLeft).coerceIn(0, displayedW - 1)
                val srcX = floor(localX.toFloat() * srcW / displayedW).toInt().coerceIn(0, srcW - 1)
                // Top bar: extend the top edge row upward
                paint.color = topEdge[srcX]
                canvas.drawLine(vpX + 0.5f, 0f, vpX + 0.5f, imgTop.toFloat(), paint)
                // Bottom bar: extend the bottom edge row downward (may be 0-height if symmetric)
                if (imgBottom < sh) {
                    paint.color = bottomEdge[srcX]
                    canvas.drawLine(vpX + 0.5f, imgBottom.toFloat(), vpX + 0.5f, sh.toFloat(), paint)
                }
            }
        }

        return outBitmap
    }

    /**
     * 1D color smoothing algorithm. 
     * Guarantees an opaque output (alpha=255) avoiding transparency bleeding.
     */
    private fun smoothColorArray(colors: IntArray, radius: Int): IntArray {
        if (radius < 1 || colors.size < 3) return colors
        val result = IntArray(colors.size)
        for (i in colors.indices) {
            var rSum = 0
            var gSum = 0
            var bSum = 0
            val jStart = maxOf(0, i - radius)
            val jEnd = minOf(colors.lastIndex, i + radius)
            for (j in jStart..jEnd) {
                val c = colors[j]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            val count = jEnd - jStart + 1
            result[i] = android.graphics.Color.argb(255, rSum / count, gSum / count, bSum / count)
        }
        return result
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
        // Atomic swap: setImageBitmap on an already-visible ImageView replaces the
        // drawable in a single frame. Do NOT toggle isVisible false->true around
        // setImageBitmap - that produces a one-frame gap that reads as a flicker
        // ("draw, erase, draw"). Only flip visibility on if this is the first
        // application after clear() / construction.
        backgroundView.setImageBitmap(bgBitmap)
        previousBitmap?.takeIf { !it.isRecycled }?.recycle()
        // Remove any leftover RenderEffect from a previous session
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(null)
        }
        if (!backgroundView.isVisible) {
            backgroundView.isVisible = true
        }
        Timber.d("DynamicBg: Background applied")
    }
}
