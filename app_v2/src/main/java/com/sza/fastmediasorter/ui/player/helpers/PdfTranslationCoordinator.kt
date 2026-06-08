package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.TranslationCacheManager
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** OCR + ML Kit translation of the currently-rendered PDF page bitmap, in either simple-overlay or Google-Lens-style layered mode. Extracted from `PdfViewerManager.translateCurrentPage*` to keep the host class under the 1000-LOC budget. */
internal class PdfTranslationCoordinator(
    // S0380: root + safeViews seam instead of ActivityPlayerUnifiedBinding (works on trimmed layouts).
    private val root: View,
    private val safeViews: PlayerBindingSafeViews,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val translationManager: TranslationManager,
    private val getCurrentPageBitmap: () -> Bitmap?,
    private val getCurrentPdfFile: () -> java.io.File?,
    private val getCurrentPdfPageIndex: () -> Int,
    private val setIsLensStyleEnabled: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onSimpleTextTranslated: (String) -> Unit,
) {

    fun translateCurrentPage() {
        if (getCurrentPageBitmap() == null) {
            onError(root.context.getString(R.string.player_page_not_ready))
            return
        }
        val filePath = getCurrentPdfFile()?.absolutePath ?: return
        android.widget.Toast.makeText(
            root.context,
            root.context.getString(R.string.translation_started),
            android.widget.Toast.LENGTH_SHORT,
        ).show()

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            setIsLensStyleEnabled(settings.translationLensStyle)
            if (settings.translationLensStyle) {
                translateLensStyle(settings, filePath)
            } else {
                translateOverlay(settings, filePath)
            }
        }
    }

    /** Simple overlay mode - one block of translated text. */
    private suspend fun translateOverlay(settings: AppSettings, filePath: String) {
        val pageIndex = getCurrentPdfPageIndex()
        val cached = TranslationCacheManager.getTranslation(filePath, pageIndex)
        if (cached != null) {
            withContext(Dispatchers.Main) {
                safeViews.translationOverlay.isVisible = true
                safeViews.tvTranslatedText.text = cached
                safeViews.translationLensOverlay.isVisible = false
            }
            return
        }
        val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
        val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        val originalBitmap = getCurrentPageBitmap() ?: return
        // For <1500px on either axis, keep original resolution for OCR accuracy; else half it.
        val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500
        val ocrBitmap = if (shouldScale) {
            Bitmap.createScaledBitmap(originalBitmap, originalBitmap.width / 2, originalBitmap.height / 2, true)
        } else {
            originalBitmap
        }
        val result = translationManager.recognizeAndTranslate(ocrBitmap, sourceLang, targetLang)
        if (shouldScale) ocrBitmap.recycle()

        withContext(Dispatchers.Main) {
            if (result != null) {
                val translatedText = result.second
                onSimpleTextTranslated(translatedText)
                safeViews.translationLensOverlay.isVisible = false
                TranslationCacheManager.putTranslation(filePath, pageIndex, translatedText)
            }
            // No text detected -> silently ignore (normal for empty pages).
        }
    }

    /** Google Lens style - translated text blocks drawn over original positions. */
    private suspend fun translateLensStyle(settings: AppSettings, filePath: String) {
        val pageIndex = getCurrentPdfPageIndex()
        val cachedBlocks = TranslationCacheManager.getLensTranslation(filePath, pageIndex)
        if (cachedBlocks != null) {
            withContext(Dispatchers.Main) { showCachedLensBlocks(cachedBlocks) }
            return
        }
        val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
        val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        val originalBitmap = getCurrentPageBitmap() ?: return
        val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500
        val ocrBitmapWidth: Int
        val ocrBitmapHeight: Int
        val ocrBitmap = if (shouldScale) {
            val scaled = Bitmap.createScaledBitmap(originalBitmap, originalBitmap.width / 2, originalBitmap.height / 2, true)
            ocrBitmapWidth = scaled.width
            ocrBitmapHeight = scaled.height
            scaled
        } else {
            ocrBitmapWidth = originalBitmap.width
            ocrBitmapHeight = originalBitmap.height
            originalBitmap
        }
        val translatedBlocks = translationManager.recognizeAndTranslateBlocks(ocrBitmap, sourceLang, targetLang)
        if (shouldScale) ocrBitmap.recycle()
        if (!translatedBlocks.isNullOrEmpty()) {
            TranslationCacheManager.putLensTranslation(filePath, pageIndex, translatedBlocks)
        }
        withContext(Dispatchers.Main) {
            if (!translatedBlocks.isNullOrEmpty()) {
                showLiveLensBlocks(originalBitmap, translatedBlocks, ocrBitmapWidth, ocrBitmapHeight)
            } else {
                safeViews.translationLensOverlay.isVisible = false
                safeViews.translationOverlay.isVisible = false
                safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
                safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE
            }
        }
    }

    private fun showCachedLensBlocks(blocks: List<TranslationManager.TranslatedTextBlock>) {
        safeViews.translationLensOverlay.setSourceBitmap(getCurrentPageBitmap())
        val overlayBlocks = blocks.map(::toOverlayBlock)
        val viewWidth = safeViews.photoView.width
        val viewHeight = safeViews.photoView.height
        val bitmapWidth = getCurrentPageBitmap()?.width ?: 1
        val bitmapHeight = getCurrentPageBitmap()?.height ?: 1
        safeViews.translationLensOverlay.setScale(bitmapWidth, bitmapHeight, viewWidth, viewHeight)
        safeViews.translationLensOverlay.setTranslatedBlocks(overlayBlocks)
        safeViews.translationLensOverlay.isVisible = true
        safeViews.translationOverlay.isVisible = false
        safeViews.btnTranslationFontDecrease?.visibility = android.view.View.VISIBLE
        safeViews.btnTranslationFontIncrease?.visibility = android.view.View.VISIBLE
    }

    private fun showLiveLensBlocks(
        originalBitmap: Bitmap,
        blocks: List<TranslationManager.TranslatedTextBlock>,
        ocrBitmapWidth: Int,
        ocrBitmapHeight: Int,
    ) {
        safeViews.translationLensOverlay.setSourceBitmap(originalBitmap)
        val overlayBlocks = blocks.map(::toOverlayBlock)
        val viewWidth = safeViews.photoView.width
        val viewHeight = safeViews.photoView.height
        safeViews.translationLensOverlay.setScale(ocrBitmapWidth, ocrBitmapHeight, viewWidth, viewHeight)
        safeViews.translationLensOverlay.setTranslatedBlocks(overlayBlocks)
        safeViews.translationLensOverlay.isVisible = true
        safeViews.translationOverlay.isVisible = false
        safeViews.btnTranslationFontDecrease?.visibility = android.view.View.VISIBLE
        safeViews.btnTranslationFontIncrease?.visibility = android.view.View.VISIBLE
    }

    private fun toOverlayBlock(block: TranslationManager.TranslatedTextBlock) =
        com.sza.fastmediasorter.ui.player.views.TranslationOverlayView.TranslatedBlock(
            originalText = block.originalText,
            translatedText = block.translatedText,
            boundingBox = block.boundingBox,
            confidence = block.confidence,
        )
}
