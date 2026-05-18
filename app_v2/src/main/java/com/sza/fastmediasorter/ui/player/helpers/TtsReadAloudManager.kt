package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.util.Locale

/**
 * Manages Text-to-Speech (TTS) "Read Aloud" feature for text viewer.
 * Uses system TextToSpeech engine - no additional dependencies required.
 */
class TtsReadAloudManager(
    private val context: Context,
    private val onStateChanged: (TtsState) -> Unit
) : TextToSpeech.OnInitListener {

    enum class TtsState {
        IDLE,        // Not reading
        INITIALIZING, // TTS engine initializing
        READING,     // Currently reading aloud
        PAUSED,      // Paused (API 21 doesn't support pause well, so we stop and track position)
        ERROR        // Initialization error
    }

    private var tts: TextToSpeech? = null
    private var state = TtsState.IDLE
    private var currentText: String = ""
    private var isInitialized = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to English
                tts?.setLanguage(Locale.ENGLISH)
                Timber.w("TTS: Default locale not supported, using English")
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    updateState(TtsState.READING)
                }

                override fun onDone(utteranceId: String?) {
                    updateState(TtsState.IDLE)
                }

                @Deprecated("Deprecated in API")
                override fun onError(utteranceId: String?) {
                    Timber.e("TTS: Error reading utterance $utteranceId")
                    updateState(TtsState.ERROR)
                }
            })

            Timber.d("TTS: Initialized successfully")

            // If text was queued before init completed, start reading now
            if (currentText.isNotEmpty() && state == TtsState.INITIALIZING) {
                speakInternal(currentText)
            }
        } else {
            Timber.e("TTS: Initialization failed with status=$status")
            updateState(TtsState.ERROR)
            Toast.makeText(context, R.string.tts_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Start reading the given text aloud.
     * If TTS is not yet initialized, queues the text for when init completes.
     */
    fun startReading(text: String) {
        if (text.isBlank()) return
        currentText = text

        if (tts == null) {
            tts = TextToSpeech(context, this)
            updateState(TtsState.INITIALIZING)
            return
        }

        if (!isInitialized) {
            updateState(TtsState.INITIALIZING)
            return
        }

        speakInternal(text)
    }

    /**
     * Stop reading aloud.
     */
    fun stop() {
        tts?.stop()
        currentText = ""
        updateState(TtsState.IDLE)
    }

    /**
     * Toggle reading: start if idle, stop if reading.
     */
    fun toggle(text: String) {
        when (state) {
            TtsState.READING -> stop()
            TtsState.IDLE, TtsState.ERROR -> startReading(text)
            TtsState.INITIALIZING -> { /* wait */ }
            TtsState.PAUSED -> startReading(text)
        }
    }

    /**
     * Release TTS resources. Call from Activity onDestroy.
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        updateState(TtsState.IDLE)
    }

    fun isReading(): Boolean = state == TtsState.READING

    fun getState(): TtsState = state

    private fun speakInternal(text: String) {
        // Split long text into chunks at sentence/paragraph boundaries (M-8 fix: don't break words)
        val maxChunk = 3500
        val chunks = splitTextAtBoundaries(text, maxChunk)

        tts?.speak(chunks.first(), TextToSpeech.QUEUE_FLUSH, null, "tts_chunk_0")
        chunks.drop(1).forEachIndexed { index, chunk ->
            tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, "tts_chunk_${index + 1}")
        }

        updateState(TtsState.READING)
        Timber.d("TTS: Started reading ${text.length} chars in ${chunks.size} chunks")
    }
    
    /**
     * Split text into chunks at word/sentence boundaries to avoid breaking words mid-utterance.
     */
    private fun splitTextAtBoundaries(text: String, maxChunk: Int): List<String> {
        if (text.length <= maxChunk) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            if (start + maxChunk >= text.length) {
                chunks.add(text.substring(start))
                break
            }
            // Search backward from max boundary for a sentence-end or whitespace
            var splitAt = start + maxChunk
            // Try sentence boundary (. ! ? followed by space/newline)
            for (i in splitAt downTo start + maxChunk / 2) {
                if (i < text.length && (text[i] == '\n' || (i > 0 && text[i - 1] in ".!?" && text[i] == ' '))) {
                    splitAt = i
                    break
                }
            }
            // If no sentence boundary found, at least split at whitespace
            if (splitAt == start + maxChunk) {
                for (i in splitAt downTo start + maxChunk / 2) {
                    if (i < text.length && text[i].isWhitespace()) {
                        splitAt = i + 1
                        break
                    }
                }
            }
            chunks.add(text.substring(start, splitAt))
            start = splitAt
        }
        return chunks
    }

    private fun updateState(newState: TtsState) {
        state = newState
        onStateChanged(newState)
    }
}
