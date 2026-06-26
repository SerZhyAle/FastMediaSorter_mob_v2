package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.utils.CharsetDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.charset.Charset

/**
 * UI setup + IO load + first-page render for a text file in [TextViewerManager]. Extracted to keep
 * the host class under the 1000-LOC budget. S0380: decoupled from `ActivityPlayerUnifiedBinding` -
 * all views go through [PlayerBindingSafeViews] (media/overlay views via nullable `*OrNull`).
 */
internal class TextViewerLoader(
    private val context: Context,
    private val safeViews: PlayerBindingSafeViews,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val networkFileManager: NetworkFileManager,
    private val textNoteStagingRegistry: LocalStagingRegistry?,
    private val applyTextFontSize: () -> Unit,
    private val closePager: () -> Unit,
    private val showError: (String) -> Unit,
    private val setCurrentLocalFile: (File?) -> Unit,
    private val setOriginalTextWithoutNumbers: (String) -> Unit,
    private val setMarkdownRendered: (Boolean) -> Unit,
    private val setSyntaxHighlightingEnabled: (Boolean) -> Unit,
    private val setCurrentReaderTheme: (TextReaderTheme) -> Unit,
    private val setCurrentCharset: (Charset) -> Unit,
    private val setTextFilePager: (TextFilePager?) -> Unit,
    private val resolveTheme: (String) -> TextReaderTheme,
    private val renderPageContent: (String, Boolean, Int) -> Unit,
    private val updatePageIndicator: () -> Unit,
    private val isAutoOpenEditMode: () -> Boolean,
    private val clearAutoOpenEditMode: () -> Unit,
    private val enterEditMode: (Boolean) -> Unit,
    private val onTextCopyClicked: () -> Unit,
    // S0704: non-null only in the unified player; null in standalone (direct progressBar write).
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) {
    /** S0704: route the load spinner through the coordinator when present, else write directly. */
    private fun setTextLoadSpinner(visible: Boolean) {
        val coord = loadingIndicatorCoordinator
        if (coord != null) {
            if (visible) coord.show(LoadingSource.TEXT_LOAD) else coord.hide(LoadingSource.TEXT_LOAD)
        } else {
            safeViews.progressBarOrNull?.isVisible = visible
        }
    }

    fun load(mediaFile: MediaFile, isWritable: Boolean) {
        closePager()
        safeViews.imageViewOrNull?.isVisible = false
        safeViews.photoViewOrNull?.isVisible = false
        safeViews.playerViewOrNull?.isVisible = false
        safeViews.audioCoverArtViewOrNull?.isVisible = false
        safeViews.audioInfoOverlayOrNull?.isVisible = false
        safeViews.setVisibleIfPresent(R.id.pdfControlsLayout, false)
        safeViews.setVisibleIfPresent(R.id.btnTranslateImage, false)
        safeViews.setVisibleIfPresent(R.id.btnGoogleLensPdfCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnOcrPdfCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnTranslatePdfCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnSearchPdfCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnSearchEpubCmd, false)
        safeViews.setVisibleIfPresent(R.id.btnTranslateEpubCmd, false)
        safeViews.epubWebViewOrNull?.isVisible = false
        safeViews.setVisibleIfPresent(R.id.epubControlsLayout, false)
        safeViews.btnExitEpubFullscreenOrNull?.isVisible = false
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        safeViews.tvTextContent.text = ""
        setTextLoadSpinner(true)
        safeViews.btnCopyTextCmd.isVisible = true
        safeViews.btnCopyTextCmd.setOnClickListener { onTextCopyClicked() }
        safeViews.btnSearchTextCmd.isVisible = true
        applyTextFontSize()
        safeViews.btnEditTextCmd.isVisible = isWritable

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                safeViews.btnTranslateTextCmd.isVisible = BuildConfig.ENABLE_TRANSLATION && settings.enableTranslation
            }
            try {
                // S0189: new note may be registered as deferred - file is created on first Save, not when editor opens. Skip not-found error in that case and render empty buffer; auto-open edit mode is next step.
                val deferredStaged = textNoteStagingRegistry?.lookup(File(mediaFile.path))
                val file = if (deferredStaged != null) deferredStaged.localFile
                else runCatching { networkFileManager.prepareFileForRead(mediaFile) }
                    .getOrElse {
                        withContext(Dispatchers.Main) {
                            setTextLoadSpinner(false)
                            showError(context.getString(R.string.text_file_load_failed))
                        }
                        return@launch
                    }

                if (!file.exists() && deferredStaged == null) {
                    withContext(Dispatchers.Main) {
                        setTextLoadSpinner(false)
                        showError(context.getString(R.string.text_file_not_found))
                    }
                    return@launch
                }

                if (!file.exists()) {
                    // Deferred new note - render empty buffer without pager (no bytes to page).
                    setCurrentLocalFile(file)
                    setOriginalTextWithoutNumbers("")
                    val s = settingsRepository.getSettings().first()
                    setMarkdownRendered(s.markdownRendered)
                    setSyntaxHighlightingEnabled(s.syntaxHighlighting)
                    setCurrentReaderTheme(resolveTheme(s.textReaderTheme))
                    withContext(Dispatchers.Main) {
                        setTextLoadSpinner(false)
                        renderPageContent("", s.showTextLineNumbers, 1)
                        safeViews.textPageNavigation.isVisible = false
                        safeViews.tvTextEncodingIndicator.text = Charsets.UTF_8.name()
                        if (isAutoOpenEditMode()) {
                            clearAutoOpenEditMode()
                            enterEditMode(true)
                        }
                    }
                    return@launch
                }

                if (file.length() > TextFilePager.MAX_FILE_SIZE) {
                    val fileSizeMb = "%.1f MB".format(file.length().toDouble() / (1024 * 1024))
                    val maxSizeMb = "%.0f MB".format(TextFilePager.MAX_FILE_SIZE.toDouble() / (1024 * 1024))
                    withContext(Dispatchers.Main) {
                        setTextLoadSpinner(false)
                        safeViews.tvTextContent.text = context.getString(R.string.text_file_too_large, fileSizeMb, maxSizeMb)
                        safeViews.textPageNavigation.isVisible = false
                    }
                    return@launch
                }

                val charset = CharsetDetector.detect(file)
                setCurrentCharset(charset)
                setCurrentLocalFile(file)
                val pager = TextFilePager(file, charset)
                pager.open()
                setTextFilePager(pager)
                val pageText = pager.readPage(0)
                setOriginalTextWithoutNumbers(pageText)
                setMarkdownRendered(settings.markdownRendered)
                setSyntaxHighlightingEnabled(settings.syntaxHighlighting)
                setCurrentReaderTheme(resolveTheme(settings.textReaderTheme))
                val startLine = pager.getStartLineNumber(0)

                withContext(Dispatchers.Main) {
                    setTextLoadSpinner(false)
                    renderPageContent(pageText, settings.showTextLineNumbers, startLine)
                    val multiPage = !pager.isSinglePage()
                    safeViews.textPageNavigation.isVisible = multiPage
                    if (multiPage) {
                        updatePageIndicator()
                        safeViews.textScrollView.setPadding(
                            safeViews.textScrollView.paddingLeft,
                            safeViews.textScrollView.paddingTop,
                            safeViews.textScrollView.paddingRight,
                            (48 * context.resources.displayMetrics.density).toInt(),
                        )
                        safeViews.btnEditTextCmd.isVisible = false
                    } else {
                        safeViews.textScrollView.setPadding(
                            safeViews.textScrollView.paddingLeft,
                            safeViews.textScrollView.paddingTop,
                            safeViews.textScrollView.paddingRight,
                            0,
                        )
                    }
                    safeViews.tvTextEncodingIndicator.text = charset.name()
                    if (isAutoOpenEditMode()) {
                        clearAutoOpenEditMode()
                        enterEditMode(true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading text file")
                withContext(Dispatchers.Main) {
                    setTextLoadSpinner(false)
                    showError(context.getString(R.string.text_file_display_error))
                }
            }
        }
    }
}
