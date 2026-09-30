package com.sza.fastmediasorter.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.domain.model.LetterboxHaloSettings
import com.sza.fastmediasorter.ui.player.letterbox.LetterboxBarsFrame
import com.sza.fastmediasorter.ui.player.letterbox.LetterboxBarsFrameBuilder
import com.sza.fastmediasorter.ui.player.letterbox.LetterboxFrameDrawable
import com.sza.fastmediasorter.ui.player.letterbox.LetterboxHaloGrowthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The player's LETTERBOX-BARS frame behind a fitted photo, with the optional LETTERBOX-HALO over it.
 *
 * The frame is built on [Dispatchers.Default] after the image is already shown (bars rule 10); the
 * main thread only swaps the drawable. The math lives in [LetterboxFillMath], the pixels in
 * [LetterboxBarsFrameBuilder], the halo in [LetterboxFrameDrawable] and its growth in
 * [LetterboxHaloGrowthManager].
 *
 * A video's first frame reuses the bars through [processFromBitmap] but never gets a halo: video is
 * outside version 0.1 of both contracts.
 */
class DynamicBackgroundProcessor(
    private val backgroundView: ImageView,
    private val coroutineScope: CoroutineScope
) {

    private var processingJob: Job? = null
    private val growth = LetterboxHaloGrowthManager(coroutineScope)
    private var currentFrame: LetterboxFrameDrawable? = null
    private var lastMediaKey: Any? = null
    private var haloSettings = LetterboxHaloSettings()
    private var isZoomed = false

    /**
     * Process [drawable] and show its bars in [backgroundView]. [screenWidth]/[screenHeight] are the
     * laid-out size of the view that displays the media (not the full screen). [mediaKey] identifies
     * the shown item: only a key different from the previous one grows the halo (halo rule 8), so a
     * re-display of the same photo goes straight to rest.
     */
    fun process(
        drawable: Drawable,
        screenWidth: Int,
        screenHeight: Int,
        mediaKey: Any? = null,
    ) {
        val isNewImage = mediaKey == null || mediaKey != lastMediaKey
        lastMediaKey = mediaKey
        build(drawable, screenWidth, screenHeight, allowHalo = true, isNewImage = isNewImage)
    }

    /**
     * Halo rule 8 across a re-inflated view: the replacement processor inherits the item already on
     * screen, so redrawing it for the new orientation goes straight to rest instead of growing.
     */
    fun adoptShownMediaKey(mediaKey: Any?) {
        lastMediaKey = mediaKey
    }

    /** Video first-frame bars: no halo and no growth, video is outside the contracts' version 0.1. */
    fun processFromBitmap(bitmap: Bitmap, screenWidth: Int, screenHeight: Int) {
        lastMediaKey = null
        build(
            BitmapDrawable(backgroundView.resources, bitmap),
            screenWidth,
            screenHeight,
            allowHalo = false,
            isNewImage = false
        )
    }

    /**
     * Halo rule 8: a settings change repaints the current frame at rest, it never grows it. The
     * growth switch and the speed only matter for the next new image.
     */
    fun setHaloSettings(settings: LetterboxHaloSettings) {
        if (settings == haloSettings) return
        val haloChanged = settings.enabled != haloSettings.enabled
        haloSettings = settings
        if (haloChanged) {
            growth.stop()
            currentFrame?.let { frame ->
                if (frame.haloAllowed) frame.haloEnabled = settings.enabled
                frame.settle()
            }
        }
    }

    /**
     * Host condition "zoomed means no bars": a photo zoomed past fit hides the frame; back at fit the
     * frame returns at rest (halo rule 8), never regrown.
     */
    fun setZoomed(zoomed: Boolean) {
        if (zoomed == isZoomed) return
        isZoomed = zoomed
        val frame = currentFrame ?: return
        if (zoomed) {
            growth.stop()
            frame.settle()
            backgroundView.isVisible = false
        } else {
            backgroundView.isVisible = true
        }
    }

    /** Bars rule 9: no frame at all - never the previous image's bars around the next one. */
    fun clear() {
        processingJob?.cancel()
        processingJob = null
        growth.stop()
        currentFrame = null
        lastMediaKey = null
        backgroundView.isVisible = false
        backgroundView.setImageDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(null)
        }
    }

    private fun build(
        drawable: Drawable,
        screenWidth: Int,
        screenHeight: Int,
        allowHalo: Boolean,
        isNewImage: Boolean,
    ) {
        processingJob?.cancel()

        // Prefer the caller-supplied view size; before layout fall back to the background view, which
        // covers the same media area.
        val resolvedW = screenWidth.takeIf { it > 0 }
            ?: backgroundView.width.takeIf { it > 0 }
            ?: return
        val resolvedH = screenHeight.takeIf { it > 0 }
            ?: backgroundView.height.takeIf { it > 0 }
            ?: return
        val background = ContextCompat.getColor(backgroundView.context, R.color.black)

        processingJob = coroutineScope.launch(Dispatchers.Default) {
            try {
                val sourceBitmap = drawableToBitmap(drawable)
                val frame = sourceBitmap?.let {
                    LetterboxBarsFrameBuilder.build(it, resolvedW, resolvedH, background)
                }
                withContext(Dispatchers.Main) {
                    if (frame == null) {
                        if (sourceBitmap == null) Timber.w("DynamicBg: no bitmap to analyse")
                        clear()
                    } else {
                        applyFrame(frame, allowHalo, isNewImage)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bars rule 10: the image stays up; the next draw repaints the background.
                Timber.e(e, "DynamicBg: Error processing background")
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "DynamicBg: OOM building the bars frame, no bars for this image")
            }
        }
    }

    private fun applyFrame(frame: LetterboxBarsFrame, allowHalo: Boolean, isNewImage: Boolean) {
        if (backgroundView.context == null) return
        val previous = currentFrame
        growth.stop()
        val next =
            LetterboxFrameDrawable(frame, haloEnabled = allowHalo && haloSettings.enabled, haloAllowed = allowHalo)
        currentFrame = next
        val grows = next.haloEnabled && isNewImage && haloSettings.growth && !isZoomed && !isReducedMotion()
        if (grows) {
            // Halo rule 7: only a frame of the same size dissolves; it is taken as it stands.
            previous?.takeIf { next.sameSizeAs(it) }?.let { old ->
                old.underlay = null
                next.underlay = old
            }
            growth.start(next, LetterboxFillMath.haloDurationMs(haloSettings.speed))
        }
        // Atomic swap on an already-visible view: toggling visibility around it reads as a flicker.
        backgroundView.setImageDrawable(next)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            backgroundView.setRenderEffect(null)
        }
        backgroundView.isVisible = !isZoomed
    }

    /**
     * Halo rule 9: the product's reduce-motion switch or the platform animator scale 0. The scale is
     * read directly because this growth is a coroutine loop, not an animator the system would zero.
     */
    private fun isReducedMotion(): Boolean =
        !AnimationPolicy.isAnimationAllowed || Settings.Global.getFloat(
            backgroundView.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }
}
