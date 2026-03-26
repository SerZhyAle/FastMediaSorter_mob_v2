package com.sza.fastmediasorter.ui.player.callbacks

import com.sza.fastmediasorter.domain.models.TranslationSessionSettings
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager

/**
 * Implementation of TranslationButtonManager.TranslationButtonCallback extracted from PlayerActivity.
 * Provides translation session state and delegates font/translation actions to Activity methods.
 */
class PlayerTranslationButtonCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : TranslationButtonManager.TranslationButtonCallback {

    override fun getTranslationSessionSettings() = activity.translationSessionSettings

    override fun setTranslationSessionSettings(settings: TranslationSessionSettings) {
        activity.translationSessionSettings = settings
    }

    override fun getCurrentFileType() = viewModel.state.value.currentFile?.type

    override fun translateCurrentImage() = activity.translateCurrentImage()

    override fun updateTextViewerTranslationButtonIcon(sourceLang: String, targetLang: String) {
        if (activity._textViewerManager != null) {
            activity.textViewerManager.updateTranslationButtonIcon(sourceLang, targetLang)
        }
    }

    override fun applyTextViewerFontSettings(settings: TranslationSessionSettings) {
        if (activity._textViewerManager != null) {
            activity.textViewerManager.applyFontSettings(settings)
        }
    }

    override fun applyTranslationManagerFontSettings(settings: TranslationSessionSettings) {
        activity.translationManager.applyFontSettings(settings)
    }

    override fun applyEpubFontSettings(settings: TranslationSessionSettings) {
        if (activity._epubViewerManager != null) {
            activity.epubViewerManager.applyFontSettings(settings)
        }
    }

    override fun forceTranslatePdf() {
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.forceTranslate()
    }

    override fun forceTranslateText() {
        if (activity._textViewerManager != null) activity.textViewerManager.forceTranslate()
    }

    override fun forceTranslateEpub() {
        if (activity._epubViewerManager != null) activity.epubViewerManager.forceTranslate()
    }
}
