package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.view.View
import androidx.lifecycle.lifecycleScope

/**
 * Manages image translation (OCR + translation overlay) in the player.
 * Handles stop/start translation, bitmap extraction, and Google Lens-style overlay.
 * Extracted from PlayerActivity to reduce its size.
 */
class PlayerImageTranslationManager(
    private val activity: PlayerActivity
) {
    private var translationJob: Job? = null

    fun stopTranslation() {
        translationJob?.cancel()
        val safeViews = activity.safeViews
        val binding = activity.activityBinding
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        binding.translationLensOverlay.isVisible = false
        binding.translationLensOverlay.clear()
        activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)

        if (binding.photoView.isVisible) {
            binding.photoView.setOnMatrixChangeListener(null)
        }

        safeViews.btnTranslateImage.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        binding.btnTranslateImageCmd.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        safeViews.btnTranslationFontDecrease?.visibility = View.GONE
        safeViews.btnTranslationFontIncrease?.visibility = View.GONE
        Timber.d("Translation stopped and overlays hidden")
    }

    fun translateCurrentImage() {
        val currentFile = activity.viewModel.state.value.currentFile
        val binding = activity.activityBinding
        val safeViews = activity.safeViews

        if (currentFile?.type != MediaType.IMAGE && currentFile?.type != MediaType.GIF) {
            binding.btnTranslateImageCmd.visibility = View.GONE
            activity.showError(activity.getString(R.string.ocr_images_only))
            return
        }

        val isCurrentlyVisible = safeViews.translationOverlay.isVisible || binding.translationLensOverlay.isVisible
        if (isCurrentlyVisible) {
            stopTranslation()
            return
        }

        translationJob?.cancel()

        val bitmap = when {
            binding.photoView.isVisible ->
                extractBitmapFromDrawable(binding.photoView.drawable, "photoView")
            binding.photoViewSurfaceB?.isVisible == true ->
                extractBitmapFromDrawable(binding.photoViewSurfaceB?.drawable, "photoViewSurfaceB")
            binding.imageView.isVisible ->
                extractBitmapFromDrawable(binding.imageView.drawable, "imageView")
            else -> {
                Timber.w(
                    "translateCurrentImage: No image view is visible " +
                        "(photoView=${binding.photoView.isVisible}, " +
                        "surfaceB=${binding.photoViewSurfaceB?.isVisible}, " +
                        "imageView=${binding.imageView.isVisible})"
                )
                null
            }
        }

        if (bitmap == null) {
            activity.showError(activity.getString(R.string.ocr_extract_image_failed))
            return
        }

        safeViews.btnTranslateImage.imageTintList = ColorStateList.valueOf(0xFFF44336.toInt())
        binding.btnTranslateImageCmd.imageTintList = ColorStateList.valueOf(0xFFF44336.toInt())
        activity.loadingIndicatorCoordinator.show(LoadingSource.TRANSLATION)

        val viewWidth = if (binding.photoView.isVisible) binding.photoView.width else binding.imageView.width
        val viewHeight = if (binding.photoView.isVisible) binding.photoView.height else binding.imageView.height

        val displayRect = if (binding.photoView.isVisible) {
            val rect = binding.photoView.displayRect
            Timber.d("TRANSLATION_DEBUG: Captured displayRect from PhotoView: $rect")
            Timber.d("TRANSLATION_DEBUG: PhotoView dimensions: ${binding.photoView.width}x${binding.photoView.height}")
            Timber.d("TRANSLATION_DEBUG: Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
            rect
        } else {
            Timber.d("TRANSLATION_DEBUG: PhotoView not visible, using ImageView")
            null
        }

        translationJob = activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val settings = activity.settingsRepository.getSettings().first()
                val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                val useLensStyle = settings.translationLensStyle

                Timber.d("TRANSLATION_DEBUG: Settings - useLensStyle=$useLensStyle, sourceLang=$sourceLang, targetLang=$targetLang")
                Timber.d("TRANSLATION_DEBUG: Bitmap size: ${bitmap.width}x${bitmap.height}")
                Timber.d("TRANSLATION_DEBUG: DisplayRect captured: $displayRect")

                if (useLensStyle) {
                    Timber.d("TRANSLATION_DEBUG: Taking GOOGLE LENS style path")
                    val lensHelper = GoogleLensTranslationHelper(
                        binding.translationLensOverlay,
                        activity.translationManager
                    )
                    lensHelper.translateBitmap(
                        bitmap = bitmap,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight,
                        displayRect = displayRect,
                        onSuccess = { _ ->
                            activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                            safeViews.translationOverlay.isVisible = false
                            if (binding.photoView.isVisible) {
                                binding.photoView.setOnMatrixChangeListener { rect ->
                                    binding.translationLensOverlay.updateImageDisplayRect(rect)
                                }
                                binding.photoView.displayRect?.let { rect ->
                                    binding.translationLensOverlay.updateImageDisplayRect(rect)
                                }
                            }
                            safeViews.btnTranslationFontDecrease?.visibility = View.VISIBLE
                            safeViews.btnTranslationFontIncrease?.visibility = View.VISIBLE
                        },
                        onEmpty = {
                            activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                            safeViews.btnTranslateImage.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            binding.btnTranslateImageCmd.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            safeViews.btnTranslationFontDecrease?.visibility = View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = View.GONE
                            activity.showError(activity.getString(R.string.translation_no_text_found))
                        },
                        onError = { message ->
                            activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                            safeViews.btnTranslateImage.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            binding.btnTranslateImageCmd.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            Timber.w("PlayerImageTranslationManager: Lens translation failed: $message")
                            activity.showError(activity.getString(R.string.translation_error))
                        }
                    )
                } else {
                    Timber.d("TRANSLATION_DEBUG: Taking LEGACY text viewer style path")
                    val result = activity.translationManager.recognizeAndTranslate(
                        bitmap = bitmap,
                        sourceLang = sourceLang,
                        targetLang = targetLang
                    )
                    withContext(Dispatchers.Main) {
                        activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                        if (result != null) {
                            val (_, translated) = result
                            if (activity._textViewerManager != null) {
                                activity.textViewerManager.displayTranslatedText(translated)
                            }
                            binding.translationLensOverlay.isVisible = false
                            safeViews.btnTranslationFontDecrease?.visibility = View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = View.GONE
                        } else {
                            safeViews.btnTranslateImage.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            binding.btnTranslateImageCmd.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                            safeViews.btnTranslationFontDecrease?.visibility = View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = View.GONE
                            activity.showError(activity.getString(R.string.translation_no_text_found))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) {
                    activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                    stopTranslation()
                    activity.showError(activity.getString(R.string.translation_error))
                }
            } finally {
                withContext(Dispatchers.Main + NonCancellable) {
                    activity.loadingIndicatorCoordinator.hide(LoadingSource.TRANSLATION)
                }
            }
        }
    }

    private fun extractBitmapFromDrawable(drawable: Drawable?, source: String): Bitmap? {
        if (drawable == null) {
            Timber.d("extractBitmapFromDrawable: drawable is null ($source)")
            return null
        }
        return when (drawable) {
            is BitmapDrawable -> {
                Timber.d("extractBitmapFromDrawable: BitmapDrawable ($source)")
                drawable.bitmap
            }
            is GifDrawable -> {
                Timber.d("extractBitmapFromDrawable: GifDrawable - extracting current frame ($source)")
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width <= 0 || height <= 0) return null
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
            is TransitionDrawable -> {
                Timber.d("extractBitmapFromDrawable: TransitionDrawable with ${drawable.numberOfLayers} layers ($source)")
                val lastLayer = drawable.getDrawable(drawable.numberOfLayers - 1)
                extractBitmapFromDrawable(lastLayer, "$source/transitionLayer")
            }
            else -> {
                Timber.d("extractBitmapFromDrawable: fallback rendering ${drawable.javaClass.simpleName} ($source)")
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
        }
    }
}
