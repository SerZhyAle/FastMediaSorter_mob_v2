package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages "text selection mode" for PDF pages.
 *
 * Because PDF pages are rendered as Bitmap (in PhotoView), the system cannot provide
 * native text selection.  This manager provides an overlay with a SelectableTextView
 * that contains the extracted page text (OCR on API < 35, PdfRenderer.Page.getTextContents()
 * on API 35+).  The DocumentSelectionActionModeCallback is attached to that view.
 *
 * Usage:
 *   enterTextSelectionMode(pageIndex, currentBitmap, pdfRenderer)
 *   exitTextSelectionMode()
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PdfTextSelectionManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val translationManager: TranslationManager,
    private val pdfDispatcher: kotlinx.coroutines.CoroutineDispatcher,
    private val onTranslateResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReadAloud: ((String) -> Unit)? = null
) {
    private var overlayView: View? = null
    private var isInTextSelectionMode = false

    fun isInTextSelectionMode(): Boolean = isInTextSelectionMode

    /**
     * Enter text selection mode for the given page.
     * Shows a progress indicator while text is being extracted, then presents a
     * SelectableTextView with the full page text.
     */
    fun enterTextSelectionMode(
        pageIndex: Int,
        currentBitmap: Bitmap?,
        pdfRenderer: PdfRenderer?
    ) {
        if (isInTextSelectionMode) {
            exitTextSelectionMode()
            return
        }

        val container = binding.root.findViewById<FrameLayout>(R.id.mediaContentArea)
            ?: run {
                Timber.e("PdfTextSelectionManager: mediaContentArea not found")
                return
            }

        // Inflate overlay
        val view = LayoutInflater.from(binding.root.context)
            .inflate(R.layout.layout_pdf_text_selection_overlay, container, false)
        container.addView(view)
        view.isVisible = true
        overlayView = view
        isInTextSelectionMode = true

        val progressLayout = view.findViewById<LinearLayout>(R.id.pdfTextExtractionProgress)
        val scrollView   = view.findViewById<ScrollView>(R.id.pdfTextSelectionScrollView)
        val tvText       = view.findViewById<TextView>(R.id.tvPdfSelectableText)
        val btnClose     = view.findViewById<View>(R.id.btnClosePdfTextSelection)

        progressLayout.isVisible = true
        scrollView.isVisible = false

        btnClose.setOnClickListener { exitTextSelectionMode() }

        coroutineScope.launch(Dispatchers.Main) {
            val pageText = withContext(Dispatchers.IO) {
                extractPageText(pageIndex, currentBitmap, pdfRenderer)
            }

            progressLayout.isVisible = false

            if (pageText.isBlank()) {
                tvText.text = binding.root.context.getString(R.string.pdf_text_empty)
            } else {
                tvText.text = pageText
                // Attach the selection ActionMode callback
                tvText.customSelectionActionModeCallback = DocumentSelectionActionModeCallback(
                    showTranslate  = BuildConfig.ENABLE_TRANSLATION,
                    showReadAloud  = onReadAloud != null,
                    getSelectedText = {
                        val start = tvText.selectionStart.coerceAtLeast(0)
                        val end   = tvText.selectionEnd.coerceAtLeast(0)
                        tvText.text?.substring(minOf(start, end), maxOf(start, end)) ?: ""
                    },
                    onTranslate    = { text ->
                        coroutineScope.launch(Dispatchers.IO) {
                            val settings = settingsRepository.getSettings().first()
                            val src = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                            val tgt = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                            val translated = translationManager.translate(text, src, tgt)
                            withContext(Dispatchers.Main) {
                                if (translated != null) onTranslateResult(translated)
                                else onError(binding.root.context.getString(R.string.translation_error))
                            }
                        }
                    },
                    onSearchGoogle = { openGoogleSearch(binding.root.context, it) },
                    onReadAloud    = onReadAloud
                )
            }

            scrollView.isVisible = true
        }
    }

    /** Hide the text selection overlay and reset state. */
    fun exitTextSelectionMode() {
        overlayView?.let { view ->
            (view.parent as? FrameLayout)?.removeView(view)
        }
        overlayView = null
        isInTextSelectionMode = false
        Timber.d("PdfTextSelectionManager: exited text selection mode")
    }

    // ── Text extraction ───────────────────────────────────────────────────────

    /**
     * Exposed for [PdfTtsDelegate] - delegates to the same extraction pipeline
     * used by the text-selection overlay.
     */
    internal suspend fun extractPageTextForTts(
        pageIndex: Int,
        bitmap: Bitmap?,
        pdfRenderer: PdfRenderer?
    ): String = extractPageText(pageIndex, bitmap, pdfRenderer)

    private suspend fun extractPageText(
        pageIndex: Int,
        bitmap: Bitmap?,
        pdfRenderer: PdfRenderer?
    ): String {
        return if (Build.VERSION.SDK_INT >= 35 && pdfRenderer != null) {
            val native = extractTextNative(pageIndex, pdfRenderer)
            if (native.isNotBlank()) native
            else if (bitmap != null) extractTextOcr(bitmap) else ""
        } else {
            if (bitmap != null) extractTextOcr(bitmap) else ""
        }
    }

    @RequiresApi(35)
    private suspend fun extractTextNative(pageIndex: Int, pdfRenderer: PdfRenderer): String {
        return withContext(pdfDispatcher) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                try {
                    @Suppress("NewApi")
                    page.getTextContents().joinToString(" ") { it.text }
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "PdfTextSelectionManager: native text extraction failed for page $pageIndex")
                ""
            }
        }
    }

    private suspend fun extractTextOcr(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val settings = settingsRepository.getSettings().first()
                val lang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                translationManager.extractTextOnly(bitmap, lang) ?: ""
            } catch (e: Exception) {
                Timber.e(e, "PdfTextSelectionManager: OCR text extraction failed")
                ""
            }
        }
    }
}
