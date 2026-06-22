package com.sza.fastmediasorter.ui.player.helpers

import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.ResourceType

/**
 * View-visibility utilities for PlayerMediaLoaderManager: hides per-viewer overlay groups
 * (image, text, PDF, EPUB) and resolves the resource type from a path scheme.
 *
 * Extracted to keep PlayerMediaLoaderManager below the 1000-line cap.
 */
class PlayerMediaViewVisibilityHelper(
    private val binding: ActivityPlayerUnifiedBinding
) {

    private val safeViews = PlayerBindingSafeViews(binding)

    /**
     * Hide image-related views.
     * @param keepAudioCover If true, don't hide audioCoverArtView (for audio files to prevent flicker)
     */
    fun hideImageViews(keepAudioCover: Boolean = false) {
        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        if (!keepAudioCover) {
            binding.audioCoverArtView.isVisible = false
        }
        safeViews.btnTranslateImage.isVisible = false
    }

    fun hideTextViewerControls() {
        safeViews.textViewerContainer.isVisible = false
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
    }

    fun hidePdfViewerControls() {
        safeViews.pdfControlsLayout.isVisible = false
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false
    }

    fun hideEpubViewerControls() {
        binding.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        binding.btnOcrEpubCmd.isVisible = false
    }

    fun determineResourceType(path: String, defaultType: ResourceType?): ResourceType = when {
        path.startsWith("cloud://") -> ResourceType.CLOUD
        path.startsWith("smb://") -> ResourceType.SMB
        path.startsWith("sftp://") -> ResourceType.SFTP
        path.startsWith("ftp://") -> ResourceType.FTP
        // Internet streams: classified before the LOCAL fallback so playVideo() dispatches them to
        // the stream helper instead of failing File.exists() (S0565). Player-local: only the playback
        // path calls this, so resource browsing is unaffected.
        path.startsWith("http://") || path.startsWith("https://") -> ResourceType.HTTP_STREAM
        path.startsWith("rtsp://") -> ResourceType.RTSP_STREAM
        else -> defaultType ?: ResourceType.LOCAL
    }
}
