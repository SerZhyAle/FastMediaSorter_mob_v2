package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import timber.log.Timber

/**
 * Manager for setting up Google Lens and OCR button click listeners.
 * Handles:
 * - Google Lens image/PDF sharing buttons (overlay + command panel)
 * - OCR buttons for images/PDFs/EPUBs with translation settings
 * - Deprecated overlay buttons maintained for compatibility
 * 
 * Created by extracting setupGoogleLensButtons() from PlayerActivity (56 lines).
 */
class GoogleLensButtonsManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val onShareToGoogleLens: () -> Unit,
    private val onSharePdfPageToGoogleLens: () -> Unit,
    private val onExtractImageText: () -> Unit,
    private val onExtractPdfText: () -> Unit,
    private val onExtractEpubText: () -> Unit,
    private val onShowTranslationSettings: () -> Unit
) {
    
    /**
     * Setup all Google Lens and OCR button click listeners.
     * Called once during PlayerActivity initialization.
     */
    fun setupButtons() {
        // ===== Google Lens Buttons =====
        
        // Deprecated overlay button (kept for compatibility)
        binding.btnGoogleLensImage.setOnClickListener {
            Timber.d("BUTTON: btnGoogleLensImage clicked (deprecated)")
            onShareToGoogleLens()
        }
        
        // New command panel button for images
        binding.btnGoogleLensImageCmd.setOnClickListener {
            Timber.d("BUTTON: btnGoogleLensImageCmd clicked")
            onShareToGoogleLens()
        }
        
        // PDF Google Lens button in command panel
        binding.btnGoogleLensPdfCmd.setOnClickListener {
            Timber.d("BUTTON: btnGoogleLensPdfCmd clicked")
            onSharePdfPageToGoogleLens()
        }
        
        // ===== OCR Buttons =====
        
        // Deprecated overlay OCR button (kept for compatibility)
        binding.btnOcrImage.setOnClickListener {
            Timber.d("BUTTON: btnOcrImage clicked (deprecated)")
            onExtractImageText()
        }
        
        // New command panel OCR button for images
        binding.btnOcrImageCmd.setOnClickListener {
            Timber.d("BUTTON: btnOcrImageCmd clicked")
            onExtractImageText()
        }
        binding.btnOcrImageCmd.setOnLongClickListener {
            Timber.d("BUTTON: btnOcrImageCmd long clicked - showing translation settings")
            onShowTranslationSettings()
            true
        }
        
        // PDF OCR button in command panel
        binding.btnOcrPdfCmd.setOnClickListener {
            Timber.d("BUTTON: btnOcrPdfCmd clicked")
            onExtractPdfText()
        }
        binding.btnOcrPdfCmd.setOnLongClickListener {
            Timber.d("BUTTON: btnOcrPdfCmd long clicked - showing translation settings")
            onShowTranslationSettings()
            true
        }
        
        // EPUB OCR button in command panel
        binding.btnOcrEpubCmd.setOnClickListener {
            Timber.d("BUTTON: btnOcrEpubCmd clicked - extracting text from EPUB")
            onExtractEpubText()
        }
        binding.btnOcrEpubCmd.setOnLongClickListener {
            Timber.d("BUTTON: btnOcrEpubCmd long clicked - showing translation settings")
            onShowTranslationSettings()
            true
        }
    }
}
