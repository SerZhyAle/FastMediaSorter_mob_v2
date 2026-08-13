package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.text.Selection
import android.text.Spannable
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
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    // S0380: root + safeViews seam instead of ActivityPlayerUnifiedBinding (works on trimmed layouts).
    private val root: View,
    private val safeViews: PlayerBindingSafeViews,
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
    private val calculatorEnabledFlow = MutableStateFlow(false)

    init {
        coroutineScope.launch {
            settingsRepository.getSettings()
                .map { it.enableCalculator }
                .distinctUntilChanged()
                .collect { enabled ->
                    calculatorEnabledFlow.value = enabled
                }
        }
    }

    fun isInTextSelectionMode(): Boolean = isInTextSelectionMode

    /**
     * Enter text selection mode for the given page.
     * Shows a progress indicator while text is being extracted, then presents a
     * SelectableTextView with the full page text.
     */
    fun enterTextSelectionMode(
        pageIndex: Int,
        currentBitmap: Bitmap?,
        pdfRenderer: PdfRenderer?,
        selectionViewPoint: PointF? = null
    ) {
        if (isInTextSelectionMode) {
            exitTextSelectionMode()
            return
        }

        val container = root.findViewById<FrameLayout>(R.id.mediaContentArea)
            ?: run {
                Timber.e("PdfTextSelectionManager: mediaContentArea not found")
                return
            }

        // Inflate overlay
        val view = LayoutInflater.from(root.context)
            .inflate(R.layout.layout_pdf_text_selection_overlay, container, false)
        container.addView(view)
        view.isVisible = true
        overlayView = view
        isInTextSelectionMode = true

        val progressLayout = view.findViewById<LinearLayout>(R.id.pdfTextExtractionProgress)
        val scrollView   = view.findViewById<ScrollView>(R.id.pdfTextSelectionScrollView)
        val tvText       = view.findViewById<TextView>(R.id.tvPdfSelectableText)
        val tvNotice     = view.findViewById<TextView>(R.id.tvPdfSelectionNotice)
        val btnClose     = view.findViewById<View>(R.id.btnClosePdfTextSelection)

        progressLayout.isVisible = true
        scrollView.isVisible = false

        btnClose.setOnClickListener { exitTextSelectionMode() }

        coroutineScope.launch(Dispatchers.Main) {
            val page = withContext(Dispatchers.IO) {
                extractPageText(pageIndex, currentBitmap, pdfRenderer)
            }
            val pageText = page.text

            progressLayout.isVisible = false

            if (pageText.isBlank()) {
                tvText.text = root.context.getString(R.string.pdf_text_empty)
            } else {
                tvText.setText(pageText, TextView.BufferType.SPANNABLE)
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
                                else onError(root.context.getString(R.string.translation_error))
                            }
                        }
                    },
                    onSearchGoogle = { openGoogleSearch(root.context, it) },
                    isCalculatorAvailable = { calculatorEnabledFlow.value },
                    onOpenCalculator = { openCalculatorForSelection(root.context, it) },
                    onReadAloud    = onReadAloud
                )

                // Pre-select the text under the long-press point. Exact on the native path (S1276),
                // approximate on the OCR one.
                if (selectionViewPoint != null && currentBitmap != null) {
                    val selected =
                        preselectWordAt(selectionViewPoint, currentBitmap, tvText, pageText, page.nativeLayout)
                    // S1276: a silent overlay with nothing selected reads as the gesture being ignored.
                    if (!selected) {
                        tvNotice.setText(R.string.pdf_text_selection_word_not_found)
                        tvNotice.isVisible = true
                    }
                }
            }

            scrollView.isVisible = true
        }
    }

    /**
     * Map the long-press [viewPoint] (PhotoView view coordinates) onto [pageText] and select that
     * text in [tvText] so the native handles and the floating Copy appear on it. Returns false when
     * nothing could be resolved, so the caller can say so instead of opening a blank-looking overlay.
     */
    private suspend fun preselectWordAt(
        viewPoint: PointF,
        bitmap: Bitmap,
        tvText: TextView,
        pageText: String,
        nativeLayout: PdfNativeTextLayout?
    ): Boolean {
        val range = resolveSelectionRange(viewPoint, bitmap, pageText, nativeLayout) ?: return false
        return applySelection(tvText, range)
    }

    private suspend fun resolveSelectionRange(
        viewPoint: PointF,
        bitmap: Bitmap,
        pageText: String,
        nativeLayout: PdfNativeTextLayout?
    ): IntRange? {
        val bitmapPoint = PdfSelectionCoordinateMapper.viewToBitmap(
            safeViews.photoView, viewPoint.x, viewPoint.y
        ) ?: return null
        // S1276: with a native layout the geometry is already in hand, so this branch runs no OCR -
        // that second full-page pass was the wait between the long-press and the handles. OCR stays
        // for API < 35 and for scanned pages, which have no text layer to read at any API level.
        val nativeRange = nativeLayout?.charRangeForPoint(bitmapPoint.x, bitmapPoint.y)
        val range = nativeRange ?: ocrCharRange(bitmapPoint, bitmap, pageText)
        val words = nativeLayout?.items?.size ?: -1
        return range
    }

    private suspend fun ocrCharRange(point: PointF, bitmap: Bitmap, pageText: String): IntRange? {
        val words = withContext(Dispatchers.IO) {
            translationManager.recognizeTextBlocksForSelection(bitmap)
        } ?: return null
        return PdfSelectionCoordinateMapper.charRangeForPoint(point, words, pageText)
    }

    private fun applySelection(tvText: TextView, range: IntRange): Boolean {
        val spannable = tvText.text as? Spannable ?: return false
        val end = range.last + 1
        val valid = range.first in 0..spannable.length && end in range.first..spannable.length
        if (valid) {
            Selection.setSelection(spannable, range.first, end)
            tvText.requestFocus()
        }
        return valid
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
    ): String = extractPageText(pageIndex, bitmap, pdfRenderer).text

    /**
     * S1276: the native route yields the page geometry alongside the text; the OCR route yields text
     * only. Carrying both together is what lets the long-press skip a second full-page pass.
     */
    private data class PageText(val text: String, val nativeLayout: PdfNativeTextLayout?)

    private suspend fun extractPageText(
        pageIndex: Int,
        bitmap: Bitmap?,
        pdfRenderer: PdfRenderer?
    ): PageText {
        if (Build.VERSION.SDK_INT >= PdfNativeTextLayout.API_NATIVE_PDF_TEXT && pdfRenderer != null) {
            val native = extractTextNative(pageIndex, pdfRenderer, bitmap)
            if (native.text.isNotBlank()) {
                return native
            }
        }
        // A scanned page carries no text layer at any API level, so OCR remains the fallback.
        return PageText(if (bitmap != null) extractTextOcr(bitmap) else "", null)
    }

    @RequiresApi(PdfNativeTextLayout.API_NATIVE_PDF_TEXT)
    private suspend fun extractTextNative(
        pageIndex: Int,
        pdfRenderer: PdfRenderer,
        bitmap: Bitmap?
    ): PageText {
        return withContext(pdfDispatcher) {
            try {
                val page = pdfRenderer.openPage(pageIndex)
                try {
                    @Suppress("NewApi")
                    val contents = page.getTextContents()
                    if (bitmap == null) {
                        // TXT-button path: no render to map onto, so text only.
                        PageText(contents.joinToString(" ") { it.text }, null)
                    } else {
                        val layout = PdfNativeTextLayout.from(
                            contents = contents,
                            pageWidth = page.width,
                            pageHeight = page.height,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                        )
                        PageText(layout.text, layout.takeIf { it.items.isNotEmpty() })
                    }
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                Timber.e(e, "PdfTextSelectionManager: native text extraction failed for page $pageIndex")
                PageText("", null)
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
