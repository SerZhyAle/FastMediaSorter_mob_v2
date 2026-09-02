package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manager for searching and displaying song lyrics for audio files.
 * Handles:
 * - Lyrics search via SearchLyricsUseCase
 * - Displaying lyrics in overlay viewer with font customization
 * - Error handling for lyrics not found or network timeouts
 * 
 * Created by extracting searchAndShowLyrics(), showLyricsViewer(), and hideLyricsViewer()
 * from PlayerActivity (~101 lines total).
 */
class LyricsManager(
    private val context: Context,
    private val binding: ActivityPlayerUnifiedBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val searchLyricsUseCase: SearchLyricsUseCase,
    private val getTranslationSessionSettings: () -> com.sza.fastmediasorter.domain.models.TranslationSessionSettings
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    /**
     * S1549: re-point every accessor at a re-inflated hierarchy. This manager is constructed once
     * per screen - its init-block settings collector must not double - so a layout re-inflate
     * re-points it instead of re-creating it.
     */
    fun rebindRoot(newRoot: android.view.View) = safeViews.rebindRoot(newRoot)
    private var ttsManager: TtsReadAloudManager? = null
    private val calculatorEnabledFlow = MutableStateFlow(false)

    init {
        // Lifetime collect: mirrors a setting into an internal flow, no View access and no LifecycleOwner
        // available here - cancelled with lifecycleScope on destroy, so repeatOnLifecycle is not needed.
        lifecycleScope.launch {
            settingsRepository.getSettings()
                .map { it.enableCalculator }
                .distinctUntilChanged()
                .collect { enabled ->
                    calculatorEnabledFlow.value = enabled
                }
        }
    }
    
    /**
     * Search and display song lyrics for audio files.
     * @param currentFile The audio file to search lyrics for
     * @param resolvedTitle Pre-resolved track title from cover search (iTunes)
     * @param resolvedArtist Pre-resolved artist name from cover search (iTunes)
     */
    fun searchAndShowLyrics(
        currentFile: MediaFile?,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null
    ) {
        if (currentFile == null || currentFile.type != MediaType.AUDIO) {
            return
        }
        
        Timber.d("LyricsManager: Searching lyrics for ${currentFile.name} (resolved: artist='$resolvedArtist', title='$resolvedTitle')")
        Toast.makeText(context, context.getString(R.string.searching_lyrics), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val result = searchLyricsUseCase.execute(currentFile, resolvedTitle, resolvedArtist)
                
                result.onSuccess { lyrics ->
                    Timber.d("LyricsManager: Lyrics found, showing viewer")
                    showLyricsViewer(
                        lyrics = lyrics,
                        currentFile = currentFile,
                        resolvedTitle = resolvedTitle,
                        resolvedArtist = resolvedArtist
                    )
                }.onFailure { error ->
                    when {
                        error.message == "Lyrics not found" ->
                            Timber.d("LyricsManager: Lyrics not found for track")
                        error is java.net.SocketTimeoutException ->
                            Timber.w("LyricsManager: Lyrics search timeout: ${error.message}")
                        else ->
                            Timber.e(error, "LyricsManager: Lyrics search failed")
                    }
                    
                    val errorMessage = when {
                        error.message == "Lyrics not found" -> context.getString(R.string.lyrics_not_found)
                        error is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout_lyrics)
                        else -> context.getString(R.string.friendly_copy_error_generic)
                    }
                    
                    Timber.d("LyricsManager: Showing toast: $errorMessage")
                    Toast.makeText(
                        context, 
                        errorMessage, 
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // This catch block might not be needed if execute() catches everything, 
                // but good for safety against unexpected crashes
                e.errorUnlessCancellation("LyricsManager: Failed to search lyrics (unexpected)")
                Toast.makeText(
                    context, 
                    context.getString(R.string.error), 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Display lyrics in overlay viewer with font customization.
     * Prepends a metadata header (Artist - Album - Title).
     */
    private fun showLyricsViewer(
        lyrics: String,
        currentFile: MediaFile,
        resolvedTitle: String? = null,
        resolvedArtist: String? = null
    ) {
        Timber.d("LyricsManager: Showing lyrics viewer")
        
        // Construct metadata header
        val headerArtist = resolvedArtist ?: currentFile.artist
        val headerTitle = resolvedTitle ?: currentFile.title
        val headerAlbum = currentFile.album
        
        val header = buildString {
            if (!headerArtist.isNullOrBlank()) append(headerArtist)
            if (!headerAlbum.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append(headerAlbum)
            }
            if (!headerTitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append(headerTitle)
            }
            if (isNotEmpty()) {
                append("\n")
                append("════════════════════════════")
                append("\n\n")
            }
        }
        
        // Filter out empty lines from lyrics
        val filteredLyrics = lyrics.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
        
        val settings = getTranslationSessionSettings()
        
        // Apply font settings from translation session
        val fontSize = when (settings.fontSize) {
            com.sza.fastmediasorter.domain.models.TranslationFontSize.MINIMUM -> 10f
            com.sza.fastmediasorter.domain.models.TranslationFontSize.SMALL -> 14f
            com.sza.fastmediasorter.domain.models.TranslationFontSize.MEDIUM -> 16f
            com.sza.fastmediasorter.domain.models.TranslationFontSize.LARGE -> 18f
            com.sza.fastmediasorter.domain.models.TranslationFontSize.HUGE -> 22f
            com.sza.fastmediasorter.domain.models.TranslationFontSize.AUTO -> 16f
        }
        
        val fontFamily = when (settings.fontFamily) {
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.SERIF -> android.graphics.Typeface.SERIF
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.MONOSPACE -> android.graphics.Typeface.MONOSPACE
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.DEFAULT -> android.graphics.Typeface.SANS_SERIF
        }
        
        safeViews.tvLyricsContent.apply {
            this.text = header + filteredLyrics
            this.textSize = fontSize
            this.typeface = fontFamily
        }

        safeViews.tvLyricsContent.customSelectionActionModeCallback =
            DocumentSelectionActionModeCallback(
                showTranslate = false,
                showReadAloud = true,
                getSelectedText = {
                    val tv = safeViews.tvLyricsContent
                    val start = tv.selectionStart.coerceAtLeast(0)
                    val end   = tv.selectionEnd.coerceAtLeast(0)
                    tv.text?.substring(minOf(start, end), maxOf(start, end)) ?: ""
                },
                onTranslate    = { },
                onSearchGoogle = { openGoogleSearch(context, it) },
                isCalculatorAvailable = { calculatorEnabledFlow.value },
                onOpenCalculator = { openCalculatorForSelection(context, it) },
                onReadAloud = { _ ->
                    ensureTtsManager().startReading(safeViews.tvLyricsContent.text.toString())
                }
            )

        safeViews.lyricsViewerContainer.isVisible = true
        // Hide top command panel when showing lyrics
        binding.topCommandPanel.isVisible = false
    }
    
    /**
     * Hide lyrics viewer overlay and restore top command panel.
     */
    fun hideLyricsViewer() {
        Timber.d("LyricsManager: Hiding lyrics viewer")
        releaseTts()
        safeViews.lyricsViewerContainer.isVisible = false
        // Restore top command panel visibility
        binding.topCommandPanel.isVisible = true
        // Force WindowInsets re-application via post() to ensure visual update
        binding.topCommandPanel.post {
            binding.topCommandPanel.requestApplyInsets()
        }
    }

    /**
     * Release owned resources without touching any Views. Safe to call from PlayerActivity.onDestroy
     * even when the lyrics viewer was never shown, and idempotent on repeated calls.
     * S0871: onDestroy does not traverse hideLyricsViewer() (back-press/close only), so the bound
     * TextToSpeech engine would otherwise outlive the Activity and leak its ServiceConnection.
     */
    fun release() {
        releaseTts()
    }

    // S0871: single teardown for the owned TTS engine, shared by hideLyricsViewer() and release().
    private fun releaseTts() {
        ttsManager?.release()
        ttsManager = null
    }

    private fun ensureTtsManager(): TtsReadAloudManager {
        return ttsManager ?: TtsReadAloudManager(context) { state ->
            Timber.d("LyricsManager: TTS state -> $state")
        }.also { ttsManager = it }
    }
}
