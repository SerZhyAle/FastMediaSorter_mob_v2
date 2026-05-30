package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
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

/** UI setup + IO load + first-page render for a text file in [TextViewerManager]. Extracted to keep the host class under the 1000-LOC budget. */
internal class TextViewerLoader(
    private val context: Context,
    private val binding: ActivityPlayerUnifiedBinding,
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
) {
    fun load(mediaFile: MediaFile, isWritable: Boolean) {
        closePager()
        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.btnTranslateImage.isVisible = false
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        binding.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        safeViews.tvTextContent.text = ""
        binding.progressBar.isVisible = true
        binding.btnCopyTextCmd.isVisible = true
        binding.btnCopyTextCmd.setOnClickListener { onTextCopyClicked() }
        binding.btnSearchTextCmd.isVisible = true
        applyTextFontSize()
        binding.btnEditTextCmd.isVisible = isWritable

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                binding.btnTranslateTextCmd.isVisible = BuildConfig.ENABLE_TRANSLATION && settings.enableTranslation
            }
            try {
                // S0189: new note may be registered as deferred - file is created on first Save, not when editor opens. Skip not-found error in that case and render empty buffer; auto-open edit mode is next step.
                val deferredStaged = textNoteStagingRegistry?.lookup(File(mediaFile.path))
                val file = if (deferredStaged != null) deferredStaged.localFile
                else runCatching { networkFileManager.prepareFileForRead(mediaFile) }
                    .getOrElse {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            showError(context.getString(R.string.text_file_load_failed))
                        }
                        return@launch
                    }

                if (!file.exists() && deferredStaged == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
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
                        binding.progressBar.isVisible = false
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
                        binding.progressBar.isVisible = false
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
                    binding.progressBar.isVisible = false
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
                        binding.btnEditTextCmd.isVisible = false
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
                    binding.progressBar.isVisible = false
                    showError(context.getString(R.string.text_file_display_error))
                }
            }
        }
    }
}
