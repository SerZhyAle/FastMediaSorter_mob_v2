package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import timber.log.Timber

class TextOcrDisplayManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val safeViews: PlayerBindingSafeViews,
    private val getTextFontSizeSp: () -> Float,
    private val getTypeface: () -> Typeface,
    private val getTextGestureDetector: () -> GestureDetector,
    private val resetTranslationState: () -> Unit,
    private val setTouchZonesEnabled: (Boolean) -> Unit,
) {
    private var previousActiveView: View? = null
    private var wasEpubWebViewVisible = false

    fun displayOcrText(text: String) {
        resetTranslationState()

        previousActiveView = when {
            binding.photoView.isVisible -> binding.photoView
            binding.imageView.isVisible -> binding.imageView
            binding.playerView.isVisible -> binding.playerView
            safeViews.pdfControlsLayout.isVisible -> safeViews.pdfControlsLayout
            binding.officeDocumentViewerContainer.isVisible -> binding.officeDocumentViewerContainer
            else -> null
        }

        binding.playerView.isVisible = false
        binding.photoView.isVisible = false
        binding.imageView.isVisible = false
        binding.officeDocumentViewerContainer.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.translationOverlay.isVisible = false
        binding.translationLensOverlay.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.btnTranslateImage.isVisible = false

        setTouchZonesEnabled(false)
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        binding.progressBar.isVisible = false
        safeViews.btnCloseTextViewer.isVisible = true

        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        binding.btnCopyTextCmd.isVisible = true

        safeViews.tvTextContent.apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextFontSizeSp())
            typeface = getTypeface()
            setTextColor(0xFF424242.toInt())
            setBackgroundColor(0xFFFFFFFF.toInt())
            setTextIsSelectable(true)
        }

        safeViews.textScrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            getTextGestureDetector().onTouchEvent(event)
            false
        }

        safeViews.textViewerContainer.setOnClickListener(null)
        Timber.d("OCR text displayed (${text.length} chars)")
    }

    fun hideOcrText() {
        safeViews.textViewerContainer.isVisible = false
        safeViews.textScrollView.isVisible = false
        safeViews.tvTextContent.text = ""
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        resetTranslationState()

        previousActiveView?.isVisible = true
        previousActiveView = null

        if (wasEpubWebViewVisible) {
            binding.epubWebView.isVisible = true
            wasEpubWebViewVisible = false
            Timber.d("OCR text hidden, EPUB WebView restored")
        }

        setTouchZonesEnabled(true)
        Timber.d("OCR text hidden, previous view restored")
    }

    fun displayTranslatedText(text: String) {
        resetTranslationState()

        val isPdfActive = safeViews.pdfControlsLayout.isVisible
        val isEpubActive = safeViews.epubControlsLayout.isVisible

        wasEpubWebViewVisible = binding.epubWebView.isVisible

        previousActiveView = when {
            binding.photoView.isVisible -> binding.photoView
            binding.imageView.isVisible -> binding.imageView
            binding.playerView.isVisible -> binding.playerView
            isPdfActive -> safeViews.pdfControlsLayout
            isEpubActive -> safeViews.epubControlsLayout
            binding.officeDocumentViewerContainer.isVisible -> binding.officeDocumentViewerContainer
            else -> null
        }

        binding.playerView.isVisible = false
        binding.photoView.isVisible = false
        binding.imageView.isVisible = false
        binding.officeDocumentViewerContainer.isVisible = false
        if (!isPdfActive) safeViews.pdfControlsLayout.isVisible = false
        if (!isEpubActive) safeViews.epubControlsLayout.isVisible = false
        binding.epubWebView.isVisible = false
        safeViews.translationOverlay.isVisible = false
        binding.translationLensOverlay.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.btnTranslateImage.isVisible = false

        setTouchZonesEnabled(false)

        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        binding.progressBar.isVisible = false
        safeViews.btnCloseTextViewer.isVisible = true

        val bottomPadding = if (isPdfActive || isEpubActive) {
            (60 * binding.root.context.resources.displayMetrics.density).toInt()
        } else {
            0
        }
        safeViews.textScrollView.setPadding(
            safeViews.textScrollView.paddingLeft,
            safeViews.textScrollView.paddingTop,
            safeViews.textScrollView.paddingRight,
            bottomPadding
        )

        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        binding.btnCopyTextCmd.isVisible = true

        safeViews.tvTextContent.apply {
            setText(text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, getTextFontSizeSp())
            typeface = getTypeface()
            setTextColor(0xFF1A237E.toInt())
            setBackgroundColor(0xFFE3F2FD.toInt())
            setTextIsSelectable(true)
        }

        safeViews.textScrollView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            getTextGestureDetector().onTouchEvent(event)
            false
        }

        safeViews.textViewerContainer.setOnClickListener(null)
        Timber.d("Translated text displayed (${text.length} chars)")
    }
}
