package com.sza.fastmediasorter.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * DynamicBackgroundProcessor
 *
 * Implements the "Ambilight" / Dynamic Background Extension effect.
 *
 * When an image is displayed and it doesn't fill the entire screen (creating letterboxing
 * or pillarboxing), this processor samples the edge colors of the image and renders a
 * blurred, extended background to fill the black bars — creating an immersive look.
 *
 * Algorithm:
 * 1. Sample a thin strip of pixels from each edge of the source drawable.
 * 2. Analyze uniformity: if all sampled colors are within a threshold → solid fill.
 *    Otherwise → gradient/smear extension.
 * 3. Render the result into the background [backgroundView] which sits behind the main image view.
 *
 * Performance:
 * - All pixel analysis happens on [Dispatchers.Default] (background thread).
 * - The result is a small [Bitmap] applied to the view on the main thread.
 * - A debounce [Job] prevents rapid successive calls from processing every frame.
 */
class DynamicBackgroundProcessor(
    private val backgroundView: ImageView,
    private val coroutineScope: CoroutineScope
) {

    private var processingJob: Job? = null

    companion object {
        /** Number of pixels to sample along each edge strip. */
        private const val SAMPLE_SIZE = 20

        /** How many pixels deep to sample from each edge. */
        private const val EDGE_DEPTH = 4

        /** Blur radius (pixels) applied to the background bitmap on API 31+. */
        private const val BLUR_RADIUS_PX = 40f

        /**
         * Color uniformity threshold (0-255 per channel).
         * If all sampled colors are within this range, treat the background as uniform.
         */
        private const val UNIFORMITY_THRESHOLD = 30

        /** Alpha value for the background overlay (0-255). Slightly transparent for subtlety. */
        private const val BACKGROUND_ALPHA = 220
    }

    /**
     * Process [drawable] and apply the dynamic background effect to [backgroundView].
     * Call from any thread — processing offloads to a background dispatcher automatically.
     *
     * @param drawable The image drawable that was just loaded into the main image view.
     * @param screenWidth Width of the screen in pixels.
     * @param screenHeight Height of the screen in pixels.
     */
    fun process(
        drawable: Drawable,
        screenWidth: Int,
        screenHeight: Int
    ) {
        // Cancel any existing processing from a previous image
        processingJob?.cancel()

        processingJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                val sourceBitmap = drawableToBitmap(drawable) ?: run {
                    Timber.w("DynamicBg: Could not convert drawable to bitmap")
                    return@launch
                }

                val bgBitmap = buildBackgroundBitmap(sourceBitmap, screenWidth, screenHeight)

                withContext(Dispatchers.Main) {
                    if (!backgroundView.context.let { it == null }) {
                        applyBackground(bgBitmap)
                    }
                }
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
        // Remove RenderEffect if previously applied
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

        // Sample edge strips
        val topColors = sampleEdge(source, Edge.TOP, srcW, srcH)
        val bottomColors = sampleEdge(source, Edge.BOTTOM, srcW, srcH)
        val leftColors = sampleEdge(source, Edge.LEFT, srcW, srcH)
        val rightColors = sampleEdge(source, Edge.RIGHT, srcW, srcH)

        val allColors = topColors + bottomColors + leftColors + rightColors

        // Build a small output bitmap (we'll scale it or use a blurred version)
        val outBitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isUniform(allColors)) {
            // Solid fill with the average color
            val avg = averageColor(allColors)
            paint.color = avg
            paint.alpha = BACKGROUND_ALPHA
            canvas.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), paint)
        } else {
            // Gradient extension: use edge gradients to paint into each quadrant
            renderGradientBackground(
                canvas, paint,
                sw.toFloat(), sh.toFloat(),
                topColors, bottomColors, leftColors, rightColors
            )
        }

        return outBitmap
    }

    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    private fun sampleEdge(bmp: Bitmap, edge: Edge, w: Int, h: Int): List<Int> {
        val colors = mutableListOf<Int>()
        val depth = EDGE_DEPTH.coerceAtMost(
            if (edge == Edge.TOP || edge == Edge.BOTTOM) h / 2 else w / 2
        ).coerceAtLeast(1)
        val samples = SAMPLE_SIZE.coerceAtMost(
            if (edge == Edge.TOP || edge == Edge.BOTTOM) w else h
        )
        when (edge) {
            Edge.TOP -> {
                val step = (w.toFloat() / samples).coerceAtLeast(1f)
                for (i in 0 until samples) {
                    val x = (i * step).toInt().coerceIn(0, w - 1)
                    for (d in 0 until depth) {
                        colors.add(bmp.getPixel(x, d.coerceIn(0, h - 1)))
                    }
                }
            }
            Edge.BOTTOM -> {
                val step = (w.toFloat() / samples).coerceAtLeast(1f)
                for (i in 0 until samples) {
                    val x = (i * step).toInt().coerceIn(0, w - 1)
                    for (d in 0 until depth) {
                        colors.add(bmp.getPixel(x, (h - 1 - d).coerceIn(0, h - 1)))
                    }
                }
            }
            Edge.LEFT -> {
                val step = (h.toFloat() / samples).coerceAtLeast(1f)
                for (i in 0 until samples) {
                    val y = (i * step).toInt().coerceIn(0, h - 1)
                    for (d in 0 until depth) {
                        colors.add(bmp.getPixel(d.coerceIn(0, w - 1), y))
                    }
                }
            }
            Edge.RIGHT -> {
                val step = (h.toFloat() / samples).coerceAtLeast(1f)
                for (i in 0 until samples) {
                    val y = (i * step).toInt().coerceIn(0, h - 1)
                    for (d in 0 until depth) {
                        colors.add(bmp.getPixel((w - 1 - d).coerceIn(0, w - 1), y))
                    }
                }
            }
        }
        return colors
    }

    private fun isUniform(colors: List<Int>): Boolean {
        if (colors.isEmpty()) return true
        var minR = 255; var maxR = 0
        var minG = 255; var maxG = 0
        var minB = 255; var maxB = 0
        for (c in colors) {
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        return (maxR - minR) < UNIFORMITY_THRESHOLD &&
            (maxG - minG) < UNIFORMITY_THRESHOLD &&
            (maxB - minB) < UNIFORMITY_THRESHOLD
    }

    private fun averageColor(colors: List<Int>): Int {
        if (colors.isEmpty()) return Color.BLACK
        var r = 0; var g = 0; var b = 0
        for (c in colors) { r += Color.red(c); g += Color.green(c); b += Color.blue(c) }
        val n = colors.size
        return Color.rgb(r / n, g / n, b / n)
    }

    private fun renderGradientBackground(
        canvas: Canvas,
        paint: Paint,
        w: Float,
        h: Float,
        topColors: List<Int>,
        bottomColors: List<Int>,
        leftColors: List<Int>,
        rightColors: List<Int>
    ) {
        // Clear with black first
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)

        val topAvg = averageColor(topColors)
        val bottomAvg = averageColor(bottomColors)
        val leftAvg = averageColor(leftColors)
        val rightAvg = averageColor(rightColors)

        // Paint four gradient rectangles from each edge toward center, overlaid with alpha
        paint.alpha = BACKGROUND_ALPHA

        // Top strip
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.5f,
            topAvg, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h * 0.5f, paint)

        // Bottom strip
        paint.shader = LinearGradient(
            0f, h, 0f, h * 0.5f,
            bottomAvg, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, h * 0.5f, w, h, paint)

        // Left strip
        paint.shader = LinearGradient(
            0f, 0f, w * 0.5f, 0f,
            leftAvg, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w * 0.5f, h, paint)

        // Right strip
        paint.shader = LinearGradient(
            w, 0f, w * 0.5f, 0f,
            rightAvg, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(w * 0.5f, 0f, w, h, paint)

        paint.shader = null
    }

    private fun applyBackground(bgBitmap: Bitmap) {
        backgroundView.setImageBitmap(bgBitmap)
        // Apply hardware blur on API 31+ for a smooth look
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(
                RenderEffect.createBlurEffect(
                    BLUR_RADIUS_PX,
                    BLUR_RADIUS_PX,
                    Shader.TileMode.CLAMP
                )
            )
        }
        backgroundView.isVisible = true
        Timber.d("DynamicBg: Background applied")
    }
}
