package com.sza.fastmediasorter.ui.player.helpers

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.RequiresApi
import android.os.ParcelFileDescriptor
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Manages PDF link detection, tap-to-open, full-text search, OCR text copy,
 * and Google Lens page sharing for PdfViewerManager.
 *
 * Extracted from PdfViewerManager (Wave 44 / S0002) to keep that class ≤ 1500 LOC.
 */
class PdfLinkAndSearchManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val translationManager: TranslationManager,
    private val onError: (String) -> Unit,
    private val onShareToGoogleLens: (File) -> Unit
) {

    // ── URL link detection cache: pageIndex → list of (boundingBoxInBitmapPx, url) ──

    private val urlBoxCache = mutableMapOf<Int, List<Pair<RectF, String>>>()

    // ── Search state ──────────────────────────────────────────────────────────────

    private val searchResults = mutableListOf<Int>() // Match positions in current page text
    private var currentSearchIndex = -1
    private var lastSearchQuery = ""
    private var currentPageText = "" // Cache of current page OCR text

    // ── Link extraction ───────────────────────────────────────────────────────────

    /**
     * Clear the URL link cache (call when a new PDF is loaded).
     */
    fun clearUrlCache() {
        urlBoxCache.clear()
    }

    /**
     * Extracts hyperlink annotations from a PDF page using the native API (Android 15 / API 35+).
     * Returns bounding boxes pre-scaled to rendered bitmap pixel coordinates so hit-testing
     * in [handlePdfTap] requires no further conversion.
     *
     * Coordinate system: PdfPageLinkContent.getBounds() uses the Android convention
     * (origin top-left, Y increases downward, units in PDF points = 1/72").
     * PdfRenderer.Page.getWidth/Height() also returns points, so the scale factor is
     * straightforward: bitmapPx = pdfPt × (bitmapDimension / pageDimensionPt).
     */
    @RequiresApi(35)
    fun extractLinksNative(file: File, pageIndex: Int, bitmapWidth: Int, bitmapHeight: Int) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: android.graphics.pdf.PdfRendererPreV? = null
        val result: List<Pair<RectF, String>> = try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = android.graphics.pdf.PdfRendererPreV(pfd)
            val page = renderer.openPage(pageIndex)
            val pageWidthPt = page.width
            val pageHeightPt = page.height
            val links = page.getLinkContents()
            page.close()

            if (links.isEmpty()) {
                Timber.d("PDF: page ${pageIndex + 1} — no links")
                emptyList()
            } else {
                val scaleX = bitmapWidth.toFloat() / pageWidthPt
                val scaleY = bitmapHeight.toFloat() / pageHeightPt

                links.flatMap { link ->
                    link.getBounds().map { bounds ->
                        Pair(
                            RectF(
                                bounds.left * scaleX, bounds.top * scaleY,
                                bounds.right * scaleX, bounds.bottom * scaleY
                            ),
                            link.getUri().toString()
                        )
                    }
                }.also { extracted ->
                    Timber.d("PDF: page ${pageIndex + 1} — ${extracted.size} link(s) extracted natively")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "PDF: native link extraction failed for page $pageIndex")
            emptyList()
        } finally {
            renderer?.close()
            pfd?.close()
        }
        urlBoxCache[pageIndex] = result
    }

    // ── Tap-to-open link ──────────────────────────────────────────────────────────

    /**
     * Called on single-tap in page mode. Maps view tap coordinates to bitmap pixel
     * coordinates via the inverted PhotoView display matrix, then checks the URL cache
     * for [currentPageIndex]. Opens the first matching link in the browser.
     * Returns true if a link was found and opened.
     */
    fun handlePdfTap(
        tapX: Float,
        tapY: Float,
        currentPageIndex: Int,
        currentBitmap: Bitmap?,
        isScrollMode: Boolean,
        pdfRendererActive: Boolean
    ): Boolean {
        if (!pdfRendererActive || isScrollMode) return false
        val bitmap = currentBitmap ?: return false
        val boxes = urlBoxCache[currentPageIndex]
        if (boxes.isNullOrEmpty()) return false

        val displayMatrix = Matrix()
        binding.photoView.getDisplayMatrix(displayMatrix)
        val invertedMatrix = Matrix()
        if (!displayMatrix.invert(invertedMatrix)) return false

        val point = floatArrayOf(tapX, tapY)
        invertedMatrix.mapPoints(point)
        val bitmapX = point[0]
        val bitmapY = point[1]

        if (bitmapX < 0 || bitmapY < 0 || bitmapX > bitmap.width || bitmapY > bitmap.height) return false

        val hitUrl = boxes.firstOrNull { (rect, _) -> rect.contains(bitmapX, bitmapY) }?.second
            ?: return false

        openUrlInBrowser(hitUrl)
        return true
    }

    private fun openUrlInBrowser(url: String) {
        val context = binding.root.context
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Timber.d("PDF: opened link → $url")
        } catch (e: Exception) {
            Timber.e(e, "PDF: failed to open URL: $url")
            onError(binding.root.context.getString(R.string.player_open_link_failed))
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────────

    /**
     * Search for text in current PDF page.
     * Returns total number of matches found on current page.
     *
     * Searches in [currentPageText] if non-blank, otherwise falls back to the global
     * [TranslationCacheManager] for the given [currentPdfPath] / [currentPageIndex].
     */
    suspend fun searchInPdf(
        query: String,
        currentPdfPath: String?,
        currentPageIndex: Int
    ): Int {
        if (query.isBlank()) {
            searchResults.clear()
            currentSearchIndex = -1
            lastSearchQuery = ""
            return 0
        }

        lastSearchQuery = query
        searchResults.clear()
        currentSearchIndex = -1

        return withContext(Dispatchers.IO) {
            val cachedText = currentPageText.ifBlank {
                com.sza.fastmediasorter.core.cache.TranslationCacheManager.getTranslation(
                    currentPdfPath ?: "",
                    currentPageIndex
                ) ?: ""
            }

            if (cachedText.isNotBlank()) {
                val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(cachedText)
                matches.forEach { searchResults.add(it.range.first) }
            }

            Timber.d("PDF search for '$query': found ${searchResults.size} matches on current page")
            searchResults.size
        }
    }

    /** Navigate to next search result. */
    fun nextSearchResult() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        Timber.d("Navigated to search result ${currentSearchIndex + 1}/${searchResults.size}")
    }

    /** Navigate to previous search result. */
    fun previousSearchResult() {
        if (searchResults.isEmpty()) return
        currentSearchIndex = if (currentSearchIndex <= 0) {
            searchResults.size - 1
        } else {
            currentSearchIndex - 1
        }
        Timber.d("Navigated to search result ${currentSearchIndex + 1}/${searchResults.size}")
    }

    /**
     * Get current search state as (currentIndex+1, totalMatches, query).
     */
    fun getSearchState(): Triple<Int, Int, String> = Triple(
        if (searchResults.isEmpty()) 0 else currentSearchIndex + 1,
        searchResults.size,
        lastSearchQuery
    )

    // ── OCR text extraction ───────────────────────────────────────────────────────

    /**
     * Extract text from [currentBitmap] using OCR (no translation).
     * Calls [onOcrResult] on the main thread with the recognized text.
     */
    fun extractTextFromCurrentPage(
        currentBitmap: Bitmap?,
        onOcrResult: (String) -> Unit
    ) {
        if (currentBitmap == null) {
            onError(binding.root.context.getString(R.string.player_page_not_ready))
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)

            val originalBitmap = currentBitmap
            val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500

            val ocrBitmap = if (shouldScale) {
                val targetWidth = 1200
                val targetHeight = (originalBitmap.height * targetWidth / originalBitmap.width.toFloat()).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val recognizedText = translationManager.extractTextOnly(ocrBitmap, sourceLang)

            if (shouldScale) ocrBitmap.recycle()

            withContext(Dispatchers.Main) {
                if (!recognizedText.isNullOrBlank()) {
                    onOcrResult(recognizedText)
                } else {
                    onError(binding.root.context.getString(com.sza.fastmediasorter.R.string.ocr_no_text_found))
                }
            }
        }
    }

    /**
     * OCR current PDF page and copy extracted text to clipboard.
     */
    fun copyPageTextToClipboard(currentBitmap: Bitmap?) {
        if (currentBitmap == null) {
            onError(binding.root.context.getString(com.sza.fastmediasorter.R.string.ocr_no_text_found))
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)

            val shouldScale = currentBitmap.width >= 1500 && currentBitmap.height >= 1500
            val ocrBitmap = if (shouldScale) {
                val targetWidth = 1200
                val targetHeight = (currentBitmap.height * targetWidth / currentBitmap.width.toFloat()).toInt()
                Bitmap.createScaledBitmap(currentBitmap, targetWidth, targetHeight, true)
            } else {
                currentBitmap
            }

            val recognizedText = translationManager.extractTextOnly(ocrBitmap, sourceLang)
            if (shouldScale) ocrBitmap.recycle()

            withContext(Dispatchers.Main) {
                if (!recognizedText.isNullOrBlank()) {
                    val ctx = binding.root.context
                    val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("pdf_text", recognizedText))
                    android.widget.Toast.makeText(
                        ctx,
                        ctx.getString(com.sza.fastmediasorter.R.string.text_copied),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onError(binding.root.context.getString(com.sza.fastmediasorter.R.string.ocr_no_text_found))
                }
            }
        }
    }

    // ── Google Lens ───────────────────────────────────────────────────────────────

    /**
     * Share [currentBitmap] to Google Lens for visual search / text recognition.
     * Saves the bitmap to a temp file, then delegates to [onShareToGoogleLens].
     */
    fun shareCurrentPageToGoogleLens(currentBitmap: Bitmap?) {
        val bitmap = currentBitmap ?: return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val context = binding.root.context
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val tempFile = File(cacheDir, "lens_share_temp.png")
                java.io.FileOutputStream(tempFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) {
                    onShareToGoogleLens(tempFile)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save PDF page for Google Lens")
                withContext(Dispatchers.Main) {
                    onError(binding.root.context.getString(R.string.toast_error_google_lens))
                }
            }
        }
    }
}
