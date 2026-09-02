package com.sza.fastmediasorter.ui.player

import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.image.ImageDisplayUtils
import com.sza.fastmediasorter.ui.player.helpers.AnimatedImageController
import com.sza.fastmediasorter.ui.player.helpers.LoadingSource
import com.sza.fastmediasorter.ui.player.helpers.PlayerLoadingIndicatorCoordinator
import timber.log.Timber

/** Glide [RequestListener]s for [ImageLoadingManager]: static [Drawable] + GIF. Extracted from the manager body to keep the host class under the 1000-LOC budget. */
internal class ImageLoadingGlideListeners(
    private val binding: ActivityPlayerUnifiedBinding,
    private val callback: ImageLoadingManager.ImageLoadingCallback,
    private val animatedImageController: AnimatedImageController,
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator,
    private val getCurrentTargetView: () -> ImageView?,
    private val getCurrentCropSetting: () -> Boolean,
    private val getCurrentIsFullscreenOrSlideshow: () -> Boolean,
    private val getCurrentDeviceWidth: () -> Int,
    private val getCurrentDeviceHeight: () -> Int,
    private val getIsDynamicBackgroundEnabled: () -> Boolean,
    private val getIsInImageDisplayMode: () -> Boolean,
    private val getDynamicBackgroundProcessor: () -> DynamicBackgroundProcessor?,
    private val setCurrentIsAnimatedContent: (Boolean) -> Unit,
    private val setPhotoViewImageLoaded: (Boolean) -> Unit,
    private val logMemoryStats: (String) -> Unit,
    // S0995: re-apply the session frame rotation after scale type is set on the freshly-bound image.
    private val reapplyRotation: () -> Unit,
) {

    fun createDrawableListener(): RequestListener<Drawable> = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean {
            if (isWebpModel(model)) {
                Timber.e(e, "WebP display failed: ${describeModel(model)} firstResource=$isFirstResource")
            }
            Timber.e(e, "ImageLoadingManager.GlideListener: onLoadFailed triggered")
            animatedImageController.onLoadFailed()
            setCurrentIsAnimatedContent(false)
            callback.setAnimatedBadgeVisible(false)
            // S0704: load failed - drop IMAGE_GLIDE and cancel its pending show + safety.
            loadingIndicatorCoordinator.reset(LoadingSource.IMAGE_GLIDE)
            if (ImageLoadingDiagnostics.isNonCriticalNetworkImageError(e)) return false
            val isRaceConditionError = e?.rootCauses?.any { cause ->
                val msg = cause.message ?: ""
                msg.contains("memory mapping") || msg.contains("setDataSource failed") || msg.contains("cancelled")
            } == true
            if (isRaceConditionError) {
                Timber.w("ImageLoadingManager: Race condition error during fast scrolling")
                if (!callback.isDestroyed()) {
                    callback.showToast(binding.root.context.getString(R.string.image_scroll_too_fast))
                }
            } else {
                Timber.e(e, "ImageLoadingManager: Failed to load image")
                if (!callback.isDestroyed()) {
                    // S2151: Glide wraps the real failure, so the root causes are offered ahead of
                    // the wrapper - classifying the bare GlideException would make every network
                    // outage look like an unclassifiable defect.
                    val causes = (e?.rootCauses?.toList() ?: emptyList()) + listOfNotNull(e)
                    if (!callback.onNetworkMediaLoadFailed(causes)) {
                        callback.showError(
                            binding.root.context.getString(R.string.error_image_load_failed),
                            e,
                        )
                    }
                }
            }
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            animatedImageController.onDrawableLoaded(resource, getCurrentTargetView())
            // Badge + play/pause follow the DECODED drawable, not the file extension: an animated
            // WebP/APNG decodes to an Animatable (AnimatedImageDrawable), a static one does not.
            val isAnimatable = resource is Animatable
            setCurrentIsAnimatedContent(isAnimatable)
            callback.setAnimatedBadgeVisible(isAnimatable)
            // S0704: image ready - drop IMAGE_GLIDE and cancel its pending show + safety so a fast
            // load can never resurrect the spinner over the displayed image.
            loadingIndicatorCoordinator.reset(LoadingSource.IMAGE_GLIDE)
            setPhotoViewImageLoaded(true)
            if (!callback.isDestroyed()) {
                applyScaleType(resource.intrinsicWidth, resource.intrinsicHeight)
                reapplyRotation()
                logMemoryStats("AFTER onResourceReady")
                val loadedBitmap = (resource as? BitmapDrawable)?.bitmap
                callback.onStaticImageLoaded(loadedBitmap)
                callback.onImageContentLoaded()
                // S0107: dynamic-background guard - drop late callbacks that arrive after the user moved to video.
                if (getIsDynamicBackgroundEnabled() && getIsInImageDisplayMode()) {
                    val tv = getCurrentTargetView()
                    val viewW = tv?.width?.takeIf { it > 0 } ?: getCurrentDeviceWidth()
                    val viewH = tv?.height?.takeIf { it > 0 } ?: getCurrentDeviceHeight()
                    getDynamicBackgroundProcessor()?.process(
                        drawable = resource,
                        screenWidth = viewW,
                        screenHeight = viewH,
                    )
                }
            }
            return false
        }
    }

    fun createGifListener(): RequestListener<GifDrawable> = object : RequestListener<GifDrawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<GifDrawable>,
            isFirstResource: Boolean,
        ): Boolean {
            if (isWebpModel(model)) {
                Timber.e(e, "WebP animated display failed: ${describeModel(model)} firstResource=$isFirstResource")
            }
            Timber.e(e, "ImageLoadingManager.GifListener: onLoadFailed triggered")
            animatedImageController.onLoadFailed()
            setCurrentIsAnimatedContent(false)
            callback.setAnimatedBadgeVisible(false)
            // S0704: GIF load failed - drop IMAGE_GLIDE and cancel its pending show + safety.
            loadingIndicatorCoordinator.reset(LoadingSource.IMAGE_GLIDE)
            if (!callback.isDestroyed()) {
                if (ImageLoadingDiagnostics.isNonCriticalNetworkImageError(e)) return false
                callback.showError(binding.root.context.getString(R.string.error_gif_load_failed), e)
            }
            return false
        }

        override fun onResourceReady(
            resource: GifDrawable,
            model: Any,
            target: Target<GifDrawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            if (isWebpModel(model)) {
                Timber.i(
                    "WebP animated display ready: ${describeModel(
                        model
                    )} dataSource=$dataSource drawable=${resource.javaClass.simpleName} " +
                        "size=${resource.intrinsicWidth}x${resource.intrinsicHeight}"
                )
            }
            animatedImageController.onDrawableLoaded(resource, getCurrentTargetView())
            // GifDrawable is always Animatable - mirror the drawable listener so the badge/toggle
            // light up now that displayImage no longer shows the badge eagerly by extension.
            setCurrentIsAnimatedContent(true)
            callback.setAnimatedBadgeVisible(true)
            // S0704: GIF ready - drop IMAGE_GLIDE and cancel its pending show + safety.
            loadingIndicatorCoordinator.reset(LoadingSource.IMAGE_GLIDE)
            setPhotoViewImageLoaded(true)
            if (!callback.isDestroyed()) {
                applyScaleType(resource.intrinsicWidth, resource.intrinsicHeight)
                reapplyRotation()
                logMemoryStats("AFTER GIF onResourceReady")
                callback.onImageContentLoaded()
            }
            return false
        }
    }

    private fun applyScaleType(imageWidth: Int, imageHeight: Int) {
        if (imageWidth <= 0 || imageHeight <= 0) return
        getCurrentTargetView()?.scaleType = ImageDisplayUtils.determineImageScaleType(
            cropImagesToFullscreen = getCurrentCropSetting(),
            isFullscreenOrSlideshow = getCurrentIsFullscreenOrSlideshow(),
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            deviceWidth = getCurrentDeviceWidth(),
            deviceHeight = getCurrentDeviceHeight(),
        )
    }

    private fun isWebpModel(model: Any?): Boolean {
        val modelValue = model?.toString().orEmpty().substringBefore('?').lowercase()
        return modelValue.endsWith(".webp") || modelValue.contains("image/webp")
    }

    private fun describeModel(model: Any?): String {
        val modelValue = model?.toString().orEmpty().substringBefore('?')
        if (modelValue.isBlank()) return "file=<unknown> source=unknown"
        val source = if (modelValue.contains("://")) modelValue.substringBefore("://") else "local"
        val fileName = modelValue.substringAfterLast('/').ifBlank { modelValue.substringAfterLast('\\') }
        return "file=${fileName.ifBlank { "<unknown>" }} source=$source"
    }
}
