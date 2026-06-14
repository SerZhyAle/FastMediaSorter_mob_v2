package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages OCR (Optical Character Recognition) operations for images.
 * 
 * Handles extracting text from images/GIFs displayed in PlayerActivity:
 * - Bitmap extraction from current image view (PhotoView or ImageView)
 * - GIF frame extraction (first frame)
 * - OCR text extraction using TranslationManager
 * - Display extracted text in TextViewerManager
 * 
 * Extracted from PlayerActivity to consolidate image OCR logic.
 */
class ImageOcrManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val translationManager: TranslationManager,
    private val textViewerManagerProvider: () -> TextViewerManager,
    private val callback: ImageOcrCallback
) {
    
    /**
     * Callback interface for OCR operations.
     */
    interface ImageOcrCallback {
        fun showError(message: String)
        fun getString(resId: Int): String
        fun getString(resId: Int, vararg formatArgs: Any): String
    }
    
    /**
     * Extract text from currently displayed image using OCR.
     * Works with regular images and GIFs (extracts first frame).
     * 
     * Note: OCR requires ENABLE_TRANSLATION=true in BuildConfig.
     * Returns early if translation/OCR is not supported by this flavor.
     */
    fun extractTextFromCurrentImage(currentFile: MediaFile?) {
        // Guard: OCR requires translation infrastructure
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("ImageOcrManager: OCR not available (ENABLE_TRANSLATION=false)")
            return
        }
        
        if (currentFile?.type != MediaType.IMAGE && currentFile?.type != MediaType.GIF) {
            callback.showError(callback.getString(R.string.ocr_images_only))
            return
        }
        
        // Get bitmap from current image view (including GIF first frame)
        val bitmap = extractBitmapFromImageView()

        if (bitmap == null) {
            callback.showError(callback.getString(R.string.ocr_extract_image_failed))
            return
        }

        // Show progress + disable the OCR button so the user gets immediate visual feedback.
        // First-call PaddleOCR/Tesseract init can take seconds (cold native library + model load);
        // without this the UI looks frozen even though the IO coroutine is running.
        binding.progressBar.isVisible = true
        binding.btnOcrImageCmd.isEnabled = false
        Timber.d("S0288: ImageOcrManager launching OCR coroutine (Dispatchers.IO)")

        // Perform OCR in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Timber.d("S0288: ImageOcrManager reading settings via Flow.first()")
                val settings = settingsRepository.getSettings().first()
                val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                Timber.d("S0288: ImageOcrManager settings read, sourceLang=$sourceLang, calling extractTextOnly")

                // Extract text using OCR (recognize facade - no translation surface)
                val recognizedText = translationManager.recognition.extractTextOnly(bitmap, sourceLang)
                Timber.d("S0288: ImageOcrManager extractTextOnly returned len=${recognizedText?.length}")

                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    binding.btnOcrImageCmd.isEnabled = true
                    if (recognizedText != null && recognizedText.isNotBlank()) {
                        textViewerManagerProvider().displayOcrText(recognizedText)
                    } else {
                        callback.showError(callback.getString(R.string.ocr_no_text_found))
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "ImageOcrManager OCR pipeline failed")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    binding.btnOcrImageCmd.isEnabled = true
                    callback.showError(callback.getString(R.string.ocr_error))
                }
            }
        }
    }
    
    /**
     * Extract bitmap from currently visible image view.
     * Handles both PhotoView (zoomable) and ImageView (quick view).
     * For GIFs, extracts the current frame.
     */
    private fun extractBitmapFromImageView(): Bitmap? {
        return when {
            binding.photoView.isVisible -> {
                binding.photoView.drawable?.let { drawable ->
                    extractBitmapFromDrawable(drawable, "photoView")
                }
            }
            binding.photoViewSurfaceB?.isVisible == true -> {
                binding.photoViewSurfaceB.drawable?.let { drawable ->
                    extractBitmapFromDrawable(drawable, "photoViewSurfaceB")
                }
            }
            binding.imageView.isVisible -> {
                binding.imageView.drawable?.let { drawable ->
                    extractBitmapFromDrawable(drawable, "imageView")
                }
            }
            else -> {
                Timber.w("extractBitmapFromImageView: No image view is visible")
                null
            }
        }
    }
    
    /**
     * Extract bitmap from a drawable.
     * Handles both BitmapDrawable and GifDrawable.
     */
    private fun extractBitmapFromDrawable(
        drawable: android.graphics.drawable.Drawable,
        source: String
    ): Bitmap? {
        return when (drawable) {
            is android.graphics.drawable.BitmapDrawable -> {
                Timber.d("ImageOcrManager: BitmapDrawable ($source)")
                drawable.bitmap
            }
            is GifDrawable -> {
                // Extract current frame from GIF by drawing to bitmap
                Timber.d("ImageOcrManager: Extracting current frame from GIF ($source)")
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width <= 0 || height <= 0) {
                    Timber.e("ImageOcrManager: Invalid GIF dimensions: ${width}x${height}")
                    return null
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
            is android.graphics.drawable.TransitionDrawable -> {
                // Glide crossfade wraps the actual drawable in TransitionDrawable
                Timber.d("ImageOcrManager: TransitionDrawable with ${drawable.numberOfLayers} layers ($source)")
                val lastLayer = drawable.getDrawable(drawable.numberOfLayers - 1)
                extractBitmapFromDrawable(lastLayer, "$source/transitionLayer")
            }
            else -> {
                // Fallback: render any drawable to bitmap
                Timber.d("ImageOcrManager: Fallback rendering ${drawable.javaClass.simpleName} ($source)")
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
