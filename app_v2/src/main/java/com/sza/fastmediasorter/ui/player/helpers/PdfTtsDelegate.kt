package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.widget.Toast
import com.sza.fastmediasorter.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Handles TTS "Read Aloud" for PdfViewerManager.
 * Extracts text from the current page via PdfTextSelectionManager, then delegates
 * to TtsReadAloudManager.  Kept as a separate delegate because PdfViewerManager
 * already exceeds 1500ines.
 */
class PdfTtsDelegate(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val pdfTextSelectionManager: PdfTextSelectionManager
) {

    private var ttsManager: TtsReadAloudManager? = null

    /**
     * Toggle TTS for the page currently displayed in the viewer.
     * Extracts text asynchronously, then starts speaking.
     */
    fun toggle(
        pageIndex: Int,
        currentBitmap: Bitmap?,
        pdfRenderer: PdfRenderer?
    ) {
        if (ttsManager?.isReading() == true) {
            ttsManager?.stop()
            return
        }

        ensureManager()

        coroutineScope.launch(Dispatchers.Main) {
            val text = withContext(Dispatchers.IO) {
                pdfTextSelectionManager.extractPageTextForTts(pageIndex, currentBitmap, pdfRenderer)
            }
            if (text.isBlank()) {
                Timber.w("PdfTtsDelegate: no text on page $pageIndex")
                Toast.makeText(context, context.getString(R.string.pdf_text_empty), Toast.LENGTH_SHORT).show()
                return@launch
            }
            Timber.d("PdfTtsDelegate: starting TTS for page $pageIndex (${text.length} chars)")
            ttsManager?.startReading(text)
        }
    }

    /** Speak [text] directly (used for selected-text TTS from ActionMode). */
    fun speakText(text: String) {
        if (text.isBlank()) return
        ensureManager()
        ttsManager?.startReading(text)
    }

    /** Immediately stop reading (e.g. on page navigation). */
    fun stop() {
        ttsManager?.stop()
    }

    /** Release TTS engine. Call from PdfViewerManager.close(). */
    fun release() {
        ttsManager?.release()
        ttsManager = null
    }

    fun isReading(): Boolean = ttsManager?.isReading() == true

    // ── private ──────────────────────────────────────────────────────────────

    private fun ensureManager() {
        if (ttsManager == null) {
            ttsManager = TtsReadAloudManager(context) { state ->
                Timber.d("PdfTtsDelegate: TTS state -> $state")
            }
        }
    }
}
