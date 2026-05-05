package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Handles TTS "Read Aloud" for EpubViewerManager.
 * Extracts current chapter text via WebView JS innerText, then delegates to TtsReadAloudManager.
 * Kept as a separate delegate because EpubViewerManager already exceeds 1500ines.
 */
class EpubTtsDelegate(private val context: Context) {

    private var ttsManager: TtsReadAloudManager? = null

    /** Toggle TTS for the chapter text currently loaded in [webView]. */
    fun toggle(webView: WebView?) {
        if (webView == null) {
            Timber.w("EpubTtsDelegate: webView is null, cannot start TTS")
            return
        }

        // If already reading, stop immediately without JS extraction.
        if (ttsManager?.isReading() == true) {
            ttsManager?.stop()
            return
        }

        ensureManager()

        webView.evaluateJavascript(
            "(function(){ return (document.documentElement.innerText || document.body.innerText || '').trim(); })()"
        ) { result ->
            val text = cleanJsString(result)
            if (text.isBlank()) {
                Timber.w("EpubTtsDelegate: no text extracted from chapter")
                Toast.makeText(context, context.getString(R.string.epub_text_empty_tts), Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            Timber.d("EpubTtsDelegate: starting TTS for ${text.length} chars")
            ttsManager?.startReading(text)
        }
    }

    /** Immediately stop reading (e.g. on chapter navigation). */
    fun stop() {
        ttsManager?.stop()
    }

    /** Speak [text] directly (used for selected-text TTS from ActionMode). */
    fun speakText(text: String) {
        if (text.isBlank()) return
        ensureManager()
        ttsManager?.startReading(text)
    }

    /** Release TTS engine. Call from EpubViewerManager.release(). */
    fun release() {
        ttsManager?.release()
        ttsManager = null
    }

    fun isReading(): Boolean = ttsManager?.isReading() == true

    // ── private ──────────────────────────────────────────────────────────────

    private fun ensureManager() {
        if (ttsManager == null) {
            ttsManager = TtsReadAloudManager(context) { state ->
                Timber.d("EpubTtsDelegate: TTS state -> $state")
            }
        }
    }

    /**
     * Clean the JSON-encoded string returned by evaluateJavascript:
     * remove surrounding quotes and unescape common sequences.
     */
    private fun cleanJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return raw.trim()
            .removeSurrounding("\"")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
    }
}
