package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel
import com.sza.fastmediasorter.ui.editor.dirty.EditorDirtyStateTracker
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Enter / exit / save lifecycle for the text-editor in [TextViewerManager]. Extracted to keep the host class under the 1000-LOC budget. */
internal class TextEditorModeController(
    private val context: Context,
    private val safeViews: PlayerBindingSafeViews,
    private val coroutineScope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val networkFileManager: NetworkFileManager,
    private val actionPanelManager: EditorActionPanel,
    private val findReplaceManager: TextEditorFindReplaceManager,
    private val dirtyTracker: EditorDirtyStateTracker<String>,
    private val editContentFlow: MutableStateFlow<String>,
    private val dirtyTextWatcher: TextWatcher,
    private val defaultTextFontSizeSp: Float,
    private val getCurrentFile: () -> MediaFile?,
    private val getOriginalTextWithoutNumbers: () -> String,
    private val setOriginalTextWithoutNumbers: (String) -> Unit,
    private val getTextFilePager: () -> TextFilePager?,
    private val getAutoSaveManager: () -> TextEditorAutoSaveManager?,
    private val setAutoSaveManager: (TextEditorAutoSaveManager?) -> Unit,
    private val getAutoFitFontManager: () -> TextEditorAutoFitFontManager?,
    private val setAutoFitFontManager: (TextEditorAutoFitFontManager?) -> Unit,
    private val getUndoRedoManager: () -> TextUndoRedoManager?,
    private val setUndoRedoManager: (TextUndoRedoManager?) -> Unit,
    private val applyLineNumbers: (String, Boolean, Int) -> String,
    private val showError: (String) -> Unit,
    // S0704: non-null only in the unified player; null in standalone (direct progressBar write).
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) {

    /** S0704: route the save spinner through the coordinator when present, else write directly. */
    private fun setTextSaveSpinner(visible: Boolean) {
        val coord = loadingIndicatorCoordinator
        if (coord != null) {
            if (visible) coord.show(LoadingSource.TEXT_SAVE) else coord.hide(LoadingSource.TEXT_SAVE)
        } else {
            safeViews.progressBarOrNull?.isVisible = visible
        }
    }

    fun enterEditMode(autoOpen: Boolean = false) {
        val pager = getTextFilePager()
        if (pager != null && !pager.isSinglePage()) {
            Toast.makeText(context, R.string.text_editing_large_file, Toast.LENGTH_SHORT).show()
            return
        }
        var textToEdit = getOriginalTextWithoutNumbers().ifBlank { safeViews.tvTextContent.text.toString() }
        val filePath = getCurrentFile()?.path ?: ""
        if (getAutoSaveManager() == null) {
            val tempDir = File(context.filesDir.parentFile, "temp")
            setAutoSaveManager(TextEditorAutoSaveManager(tempDir, coroutineScope))
        }
        val draft = getAutoSaveManager()?.restoreDraft(filePath)
        if (draft != null && draft != textToEdit) {
            textToEdit = draft
            Toast.makeText(context, R.string.draft_restored, Toast.LENGTH_LONG).show()
        }
        safeViews.etTextContent.setText(textToEdit)
        safeViews.textScrollView.isVisible = false
        safeViews.btnCopyTextCmd.isVisible = false
        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false
        safeViews.textEditContainer.isVisible = true
        safeViews.etTextContent.requestFocus()
        // S0189 Phase 09: bind dirty-state tracker to EditText and reset panel tint. Text watcher pumps content into [editContentFlow]; tracker compares against baseline we set via rebaseline().
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
        editContentFlow.value = textToEdit
        dirtyTracker.rebaseline(textToEdit)
        safeViews.etTextContent.addTextChangedListener(dirtyTextWatcher)
        actionPanelManager.onEnterEditMode()
        // S0189 Phase 07: auto-fit font - uses max of persistent setting; locks on manual swipe.
        val maxFontSp = defaultTextFontSizeSp * com.sza.fastmediasorter.domain.models.TranslationFontSize.HUGE.multiplier
        getAutoFitFontManager()?.detach()
        setAutoFitFontManager(
            TextEditorAutoFitFontManager(
                editText = safeViews.etTextContent,
                scrollView = safeViews.textEditScrollView,
                maxSizeSp = maxFontSp,
            ).also { it.attach(); it.reset() }
        )
        getUndoRedoManager()?.detach() // M-10 fix.
        setUndoRedoManager(TextUndoRedoManager(safeViews.etTextContent) { canUndo, canRedo ->
            safeViews.btnUndo.alpha = if (canUndo) 1f else 0.3f
            safeViews.btnUndo.isEnabled = canUndo
            safeViews.btnRedo.alpha = if (canRedo) 1f else 0.3f
            safeViews.btnRedo.isEnabled = canRedo
        }.also { it.attach() })
        getAutoSaveManager()?.startAutoSave(safeViews.etTextContent, filePath)
        findReplaceManager.setupCursorPositionTracking()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(safeViews.etTextContent, InputMethodManager.SHOW_IMPLICIT)
    }

    fun exitEditMode() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(safeViews.etTextContent.windowToken, 0)
        getUndoRedoManager()?.detach()
        setUndoRedoManager(null)
        getAutoSaveManager()?.stopAutoSave()
        // S0189 Phase 09: detach text watcher (subsequent EditText changes don't dirty the tracker once editor is closed) and reset panel tint.
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
        actionPanelManager.onExitEditMode()
        // S0189 Phase 07: detach auto-fit font manager.
        getAutoFitFontManager()?.detach()
        setAutoFitFontManager(null)
        findReplaceManager.closeFindPanel()
        safeViews.textEditContainer.isVisible = false
        safeViews.textScrollView.isVisible = true
        safeViews.btnCopyTextCmd.isVisible = true
        safeViews.btnEditTextCmd.isVisible = true
        safeViews.btnSearchTextCmd.isVisible = true
    }

    fun saveEditedText() {
        val newText = safeViews.etTextContent.text.toString()
        val fileToSave = getCurrentFile() ?: run {
            showError(context.getString(R.string.text_file_not_found))
            return
        }
        setTextSaveSpinner(true)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val localFile = networkFileManager.prepareFileForWrite(fileToSave)
                if (localFile == null) {
                    withContext(Dispatchers.Main) { setTextSaveSpinner(false) }
                    return@launch
                }
                localFile.writeText(newText)
                MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "text-edit")
                val isNetworkFile = !fileToSave.path.startsWith("/")
                if (isNetworkFile) {
                    val uploadSuccess = networkFileManager.uploadEditedFile(fileToSave, localFile)
                    if (!uploadSuccess) {
                        withContext(Dispatchers.Main) {
                            setTextSaveSpinner(false)
                            showError(context.getString(R.string.text_file_upload_failed_after_local_save))
                        }
                        return@launch
                    }
                    networkFileManager.clearEditingCache()
                }
                withContext(Dispatchers.Main) {
                    setTextSaveSpinner(false)
                    setOriginalTextWithoutNumbers(newText)
                }
                val settings = settingsRepository.getSettings().first() // C-3 fix: read settings on IO, not Main.
                withContext(Dispatchers.Main) {
                    safeViews.tvTextContent.text = applyLineNumbers(newText, settings.showTextLineNumbers, 1)
                    getAutoSaveManager()?.stopAutoSave(deleteDraft = true)
                    exitEditMode()
                    Toast.makeText(context, R.string.toast_text_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.errorUnlessCancellation("Error saving text file")
                withContext(Dispatchers.Main) {
                    setTextSaveSpinner(false)
                    showError(context.getString(R.string.text_file_save_failed))
                }
            }
        }
    }
}
