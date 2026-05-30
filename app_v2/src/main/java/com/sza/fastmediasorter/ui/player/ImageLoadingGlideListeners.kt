package com.sza.fastmediasorter.ui.player

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.view.isVisible
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.helpers.AnimatedImageController
import com.sza.fastmediasorter.ui.image.ImageDisplayUtils
import timber.log.Timber

/** Glide [RequestListener]s for [ImageLoadingManager]: static [Drawable] + GIF. Extracted from the manager body to keep the host class under the 1000-LOC budget. */
internal class ImageLoadingGlideListeners(
    private val binding: ActivityPlayerUnifiedBinding,
    private val callback: ImageLoadingManager.ImageLoadingCallback,
    private val animatedImageController: AnimatedImageController,
    private val loadingIndicatorHandler: android.os.Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val hideLoadingSafetyRunnable: Runnable,
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
) {

    fun createDrawableListener(): RequestListener<Drawable> = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean {
            Timber.e(e, "ImageLoadingManager.GlideListener: onLoadFailed triggered")
            animatedImageController.onLoadFailed()
            setCurrentIsAnimatedContent(false)
            callback.setAnimatedBadgeVisible(false)
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
            if (!callback.isDestroyed()) binding.progressBar.isVisible = false
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
                    callback.showError(binding.root.context.getString(R.string.error_image_load_failed), e)
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
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
            setPhotoViewImageLoaded(true)
            if (!callback.isDestroyed()) {
                binding.progressBar.isVisible = false
                applyScaleType(resource.intrinsicWidth, resource.intrinsicHeight)
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
                        drawable = resource, screenWidth = viewW, screenHeight = viewH,
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
            Timber.e(e, "ImageLoadingManager.GifListener: onLoadFailed triggered")
            animatedImageController.onLoadFailed()
            setCurrentIsAnimatedContent(false)
            callback.setAnimatedBadgeVisible(false)
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
            if (!callback.isDestroyed()) {
                binding.progressBar.isVisible = false
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
            animatedImageController.onDrawableLoaded(resource, getCurrentTargetView())
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            loadingIndicatorHandler.removeCallbacks(hideLoadingSafetyRunnable)
            setPhotoViewImageLoaded(true)
            if (!callback.isDestroyed()) {
                binding.progressBar.isVisible = false
                applyScaleType(resource.intrinsicWidth, resource.intrinsicHeight)
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
}
