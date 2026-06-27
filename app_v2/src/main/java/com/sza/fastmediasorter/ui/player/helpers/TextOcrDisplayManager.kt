package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import timber.log.Timber

/**
 * S0380: decoupled from `ActivityPlayerUnifiedBinding` - now drives all views through
 * [PlayerBindingSafeViews]. Media/overlay views it hides are accessed via the nullable
 * `*OrNull` accessors, so a trimmed standalone layout (which never triggers OCR) can omit them.
 */
class TextOcrDisplayManager(
    private val safeViews: PlayerBindingSafeViews,
    private val getTextFontSizeSp: () -> Float,
    private val getTypeface: () -> Typeface,
    private val getTextGestureDetector: () -> GestureDetector,
    private val resetTranslationState: () -> Unit,
    private val setTouchZonesEnabled: (Boolean) -> Unit,
    // S0704: non-null only in the unified player; null in standalone (direct progressBar write).
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) {
    private var previousActiveView: View? = null
    private var wasEpubWebViewVisible = false

    /**
     * S0704: the OCR/translation result is being shown, so the operation spinner must be off. The
     * driving operation (ImageOcrManager / PlayerImageTranslationManager) already hides its own
     * source in its finally; this is the display-side safety. Routes through the coordinator when
     * present, else writes the bar directly (standalone).
     */
    private fun hideOcrSpinner() {
        val coord = loadingIndicatorCoordinator
        if (coord != null) {
            coord.hide(LoadingSource.OCR)
        } else {
            safeViews.progressBarOrNull?.isVisible = false
        }
    }

    fun displayOcrText(text: String) {
        resetTranslationState()

        previousActiveView = when {
            safeViews.photoViewOrNull?.isVisible == true -> safeViews.photoViewOrNull
            safeViews.imageViewOrNull?.isVisible == true -> safeViews.imageViewOrNull
            safeViews.playerViewOrNull?.isVisible == true -> safeViews.playerViewOrNull
            safeViews.pdfControlsLayout.isVisible -> safeViews.pdfControlsLayout
            safeViews.officeDocumentViewerContainerOrNull?.isVisible == true -> safeViews.officeDocumentViewerContainerOrNull
            else -> null
        }

        safeViews.playerViewOrNull?.isVisible = false
        safeViews.photoViewOrNull?.isVisible = false
        safeViews.imageViewOrNull?.isVisible = false
        safeViews.officeDocumentViewerContainerOrNull?.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.translationOverlay.isVisible = false
        safeViews.translationLensOverlayOrNull?.isVisible = false
        safeViews.audioCoverArtViewOrNull?.isVisible = false
        safeViews.audioInfoOverlayOrNull?.isVisible = false
        safeViews.btnTranslateImage.isVisible = false

        setTouchZonesEnabled(false)
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        hideOcrSpinner()
        safeViews.btnCloseTextViewer.isVisible = true

        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false
        safeViews.btnCopyTextCmd.isVisible = true

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
            safeViews.epubWebViewOrNull?.isVisible = true
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

        wasEpubWebViewVisible = safeViews.epubWebViewOrNull?.isVisible == true

        previousActiveView = when {
            safeViews.photoViewOrNull?.isVisible == true -> safeViews.photoViewOrNull
            safeViews.imageViewOrNull?.isVisible == true -> safeViews.imageViewOrNull
            safeViews.playerViewOrNull?.isVisible == true -> safeViews.playerViewOrNull
            isPdfActive -> safeViews.pdfControlsLayout
            isEpubActive -> safeViews.epubControlsLayout
            safeViews.officeDocumentViewerContainerOrNull?.isVisible == true -> safeViews.officeDocumentViewerContainerOrNull
            else -> null
        }

        safeViews.playerViewOrNull?.isVisible = false
        safeViews.photoViewOrNull?.isVisible = false
        safeViews.imageViewOrNull?.isVisible = false
        safeViews.officeDocumentViewerContainerOrNull?.isVisible = false
        if (!isPdfActive) safeViews.pdfControlsLayout.isVisible = false
        if (!isEpubActive) safeViews.epubControlsLayout.isVisible = false
        safeViews.epubWebViewOrNull?.isVisible = false
        safeViews.translationOverlay.isVisible = false
        safeViews.translationLensOverlayOrNull?.isVisible = false
        safeViews.audioCoverArtViewOrNull?.isVisible = false
        safeViews.audioInfoOverlayOrNull?.isVisible = false
        safeViews.btnTranslateImage.isVisible = false

        setTouchZonesEnabled(false)

        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        hideOcrSpinner()
        safeViews.btnCloseTextViewer.isVisible = true

        val bottomPadding = if (isPdfActive || isEpubActive) {
            (60 * safeViews.textScrollView.context.resources.displayMetrics.density).toInt()
        } else {
            0
        }
        safeViews.textScrollView.setPadding(
            safeViews.textScrollView.paddingLeft,
            safeViews.textScrollView.paddingTop,
            safeViews.textScrollView.paddingRight,
            bottomPadding
        )

        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false
        safeViews.btnCopyTextCmd.isVisible = true

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
