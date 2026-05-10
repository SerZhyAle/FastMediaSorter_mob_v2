package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.view.View
import com.sza.fastmediasorter.ui.player.views.TranslationOverlayView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Helper class for Google Lens style translation overlay.
 * Provides unified translation experience for both images and PDFs.
 * 
 * Usage:
 * 1. Create instance with overlay view and translation manager
 * 2. Call translateBitmap() with source bitmap
 * 3. Overlay will show translated text blocks over original positions
 */
class GoogleLensTranslationHelper(
    private val translationOverlayView: TranslationOverlayView,
    private val translationManager: TranslationManager
) {
    
    /**
     * Maximum bitmap dimension for OCR (to avoid OOM)
     */
    private val maxOcrDimension = 2048
    
    /**
     * Translate bitmap and show results in Google Lens style overlay
     * 
     * @param bitmap Source bitmap to translate
     * @param sourceLang Source language code (or empty for auto-detect)
     * @param targetLang Target language code
     * @param viewWidth Width of the view displaying the image
     * @param viewHeight Height of the view displaying the image
     * @param displayRect Optional actual display rectangle of the image (for PhotoView zoom/pan)
     * @param onSuccess Called when translation succeeds with block count
     * @param onEmpty Called when no text was detected
     * @param onError Called on translation error with message
     */
    suspend fun translateBitmap(
        bitmap: Bitmap,
        sourceLang: String,
        targetLang: String,
        viewWidth: Int,
        viewHeight: Int,
        displayRect: android.graphics.RectF? = null,
        onSuccess: (blockCount: Int) -> Unit = {},
        onEmpty: () -> Unit = {},
        onError: (message: String) -> Unit = {}
    ) {
        try {
            // Determine if scaling is needed for large bitmaps
            val shouldScale = bitmap.width > maxOcrDimension || bitmap.height > maxOcrDimension
            
            val ocrBitmapWidth: Int
            val ocrBitmapHeight: Int
            
            val ocrBitmap = if (shouldScale) {
                val scale = minOf(
                    maxOcrDimension.toFloat() / bitmap.width,
                    maxOcrDimension.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                Timber.d("Scaling bitmap for OCR: ${bitmap.width}x${bitmap.height} → ${newWidth}x${newHeight}")
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                ocrBitmapWidth = scaled.width
                ocrBitmapHeight = scaled.height
                scaled
            } else {
                Timber.d("Using original bitmap for OCR: ${bitmap.width}x${bitmap.height}")
                ocrBitmapWidth = bitmap.width
                ocrBitmapHeight = bitmap.height
                bitmap
            }
            
            // Perform OCR and translation
            val translatedBlocks = translationManager.recognizeAndTranslateBlocks(
                ocrBitmap,
                sourceLang,
                targetLang
            )
            
            withContext(Dispatchers.Main) {
                if (translatedBlocks != null && translatedBlocks.isNotEmpty()) {
                    // Set source bitmap for color sampling (use original bitmap, not scaled)
                    translationOverlayView.setSourceBitmap(bitmap)
                    
                    // Convert TranslatedTextBlock to TranslationOverlayView.TranslatedBlock
                    val overlayBlocks = translatedBlocks.map { block ->
                        TranslationOverlayView.TranslatedBlock(
                            originalText = block.originalText,
                            translatedText = block.translatedText,
                            boundingBox = block.boundingBox,
                            confidence = block.confidence
                        )
                    }
                    
                    // Set scale factor for coordinate conversion
                    // If displayRect is provided (PhotoView zoom/pan), use it directly
                    // Otherwise, calculate scale based on view dimensions
                    if (displayRect != null) {
                        Timber.d("TRANSLATION_DEBUG: Using provided displayRect for overlay: $displayRect")
                        Timber.d("TRANSLATION_DEBUG: OCR bitmap size: ${ocrBitmapWidth}x${ocrBitmapHeight}")
                        Timber.d("TRANSLATION_DEBUG: View size: ${viewWidth}x${viewHeight}")
                        Timber.d("TRANSLATION_DEBUG: First block bounding box: ${overlayBlocks.firstOrNull()?.boundingBox}")
                        translationOverlayView.setOriginalImageSize(ocrBitmapWidth, ocrBitmapHeight)
                        translationOverlayView.updateImageDisplayRect(displayRect)
                    } else {
                        Timber.d("TRANSLATION_DEBUG: No displayRect, using setScale fallback")
                        Timber.d("TRANSLATION_DEBUG: OCR bitmap: ${ocrBitmapWidth}x${ocrBitmapHeight}, View: ${viewWidth}x${viewHeight}")
                        translationOverlayView.setScale(
                            ocrBitmapWidth,
                            ocrBitmapHeight,
                            viewWidth,
                            viewHeight
                        )
                    }
                    
                    // Update overlay and show
                    translationOverlayView.setTranslatedBlocks(overlayBlocks)
                    translationOverlayView.visibility = View.VISIBLE
                    
                    Timber.d("Displaying ${overlayBlocks.size} translated blocks in Lens style")
                    onSuccess(overlayBlocks.size)
                } else {
                    // No text detected
                    translationOverlayView.visibility = View.GONE
                    Timber.d("No text detected for translation")
                    onEmpty()
                }
            }
            
        } catch (e: CancellationException) {
            Timber.d("Google Lens translation cancelled")
            withContext(Dispatchers.Main) {
                translationOverlayView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Timber.e(e, "Google Lens translation failed")
            withContext(Dispatchers.Main) {
                translationOverlayView.visibility = View.GONE
                onError(translationOverlayView.context.getString(com.sza.fastmediasorter.R.string.translation_error))
            }
        }
        // NOTE: Do NOT recycle ocrBitmap here.
        // On cancellation, Tesseract native code may still reference the bitmap → SIGSEGV.
        // GC will handle the small scaled copy.
    }
    
    /**
     * Hide the translation overlay
     */
    fun hide() {
        translationOverlayView.visibility = View.GONE
        translationOverlayView.clear()
    }
    
    /**
     * Check if overlay is currently visible
     */
    fun isVisible(): Boolean = translationOverlayView.visibility == View.VISIBLE
}
