package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase
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
    private val searchLyricsUseCase: SearchLyricsUseCase,
    private val getTranslationSessionSettings: () -> com.sza.fastmediasorter.domain.models.TranslationSessionSettings
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    
    /**
     * Search and display song lyrics for audio files.
     */
    fun searchAndShowLyrics(currentFile: MediaFile?) {
        if (currentFile == null || currentFile.type != MediaType.AUDIO) {
            return
        }
        
        Timber.d("LyricsManager: Searching lyrics for ${currentFile.name}")
        Toast.makeText(context, context.getString(R.string.searching_lyrics), Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val result = searchLyricsUseCase.execute(currentFile)
                
                result.onSuccess { lyrics ->
                    Timber.d("LyricsManager: Lyrics found, showing viewer")
                    showLyricsViewer(lyrics)
                }.onFailure { error ->
                    if (error is java.net.SocketTimeoutException) {
                        Timber.w("LyricsManager: Lyrics search timeout: ${error.message}")
                    } else {
                        Timber.e(error, "LyricsManager: Lyrics search failed")
                    }
                    
                    val errorMessage = when {
                        error.message == "Lyrics not found" -> context.getString(R.string.lyrics_not_found)
                        error is java.net.SocketTimeoutException -> context.getString(R.string.error_timeout_lyrics)
                        else -> "Error: ${error.localizedMessage ?: "Unknown error"}"
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
                Timber.e(e, "LyricsManager: Failed to search lyrics (unexpected)")
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
     */
    private fun showLyricsViewer(lyrics: String) {
        Timber.d("LyricsManager: Showing lyrics viewer")
        
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
            this.text = filteredLyrics
            this.textSize = fontSize
            this.typeface = fontFamily
        }
        
        safeViews.lyricsViewerContainer.isVisible = true
        // Hide top command panel when showing lyrics
        binding.topCommandPanel.isVisible = false
    }
    
    /**
     * Hide lyrics viewer overlay and restore top command panel.
     */
    fun hideLyricsViewer() {
        Timber.d("LyricsManager: Hiding lyrics viewer")
        safeViews.lyricsViewerContainer.isVisible = false
        // Restore top command panel visibility
        binding.topCommandPanel.isVisible = true
        // Force WindowInsets re-application via post() to ensure visual update
        binding.topCommandPanel.post { 
            binding.topCommandPanel.requestApplyInsets() 
        }
    }
}
