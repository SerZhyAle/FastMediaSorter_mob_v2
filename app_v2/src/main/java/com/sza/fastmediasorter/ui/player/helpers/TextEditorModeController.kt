package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Rect
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.showSoftInputImplicitly
import com.sza.fastmediasorter.ui.editor.actions.EditorActionPanel
import com.sza.fastmediasorter.ui.editor.dirty.EditorDirtyStateTracker
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** Enter / exit / save lifecycle for the text-editor in [TextViewerManager]. Extracted to keep the host class under the 1000-LOC budget. */
@Suppress("LongParameterList") // the host's editor state is passed as accessors, one per field it owns
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
    private val ioDispatcher: CoroutineDispatcher,
    // S0704: non-null only in the unified player; null in standalone (direct progressBar write).
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) {

    // A property, not a constructor parameter: the host command panel lays itself out around the
    // editor state and the editor owns when that state changes, but the host wires this after construction.
    var onEditorVisibilityChanged: () -> Unit = {}

    // The editor opens after an off-main draft read; a repeated tap or exitEditMode must cancel the pending open.
    private var pendingEnterJob: Job? = null
    private var documentSaveJob: Job? = null
    private var editorSession = 0
    private val documentSaveManager by lazy { TextDocumentSaveManager(context, ioDispatcher) }

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
        Timber.d("S4580: enterEditMode reads the draft on IO before showEditor")
        val pager = getTextFilePager()
        if (pager != null && !pager.isSinglePage()) {
            Toast.makeText(context, R.string.text_editing_large_file, Toast.LENGTH_SHORT).show()
            return
        }
        val originalText = getOriginalTextWithoutNumbers().ifBlank { safeViews.tvTextContent.text.toString() }
        val filePath = getCurrentFile()?.path ?: ""
        val existingManager = getAutoSaveManager()
        pendingEnterJob?.cancel()
        pendingEnterJob = coroutineScope.launch {
            // Resolving filesDir and reading the draft are disk access; the editor opens once they return.
            val (manager, draft) = withContext(ioDispatcher) {
                val resolved = existingManager ?: TextEditorAutoSaveManager(
                    tempDir = File(context.filesDir.parentFile, "temp"),
                    coroutineScope = coroutineScope,
                    ioDispatcher = ioDispatcher,
                )
                resolved to resolved.restoreDraft(filePath)
            }
            if (getAutoSaveManager() == null) setAutoSaveManager(manager)
            // The user may have paged to another file while the draft was read.
            if (getCurrentFile()?.path.orEmpty() != filePath) return@launch
            showEditor(originalText, draft, filePath)
        }
    }

    private fun showEditor(originalText: String, draft: String?, filePath: String) {
        editorSession++
        var textToEdit = originalText
        if (draft != null && draft != textToEdit) {
            textToEdit = draft
            Toast.makeText(context, R.string.draft_restored, Toast.LENGTH_LONG).show()
        }
        safeViews.etTextContent.setText(textToEdit)
        showEditorViews()
        // S0189 Phase 09: bind dirty-state tracker to EditText and reset panel tint. Text watcher pumps content into [editContentFlow]; tracker compares against baseline we set via rebaseline().
        editContentFlow.value = textToEdit
        dirtyTracker.rebaseline(if (filePath.startsWith("content://")) originalText else textToEdit)
        attachEditorHelpers(filePath)
    }

    /**
     * S4714: release every listener and helper bound to the current editor views before the host
     * discards them. Visibility, the dirty baseline and the draft file are left alone: the editor is
     * still open, only its views are being replaced.
     */
    fun detachFromViews() {
        pendingEnterJob?.cancel()
        pendingEnterJob = null
        releaseEditorHelpers()
    }

    private fun releaseEditorHelpers() {
        unbindImeInsets()
        getUndoRedoManager()?.detach()
        setUndoRedoManager(null)
        getAutoSaveManager()?.stopAutoSave()
        // S0189 Phase 09: once the editor closes, later EditText changes must not dirty the tracker.
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
        findReplaceManager.detachCursorPositionTracking()
        // S0189 Phase 07: detach auto-fit font manager.
        getAutoFitFontManager()?.detach()
        setAutoFitFontManager(null)
    }

    /**
     * S4714: re-open the editor on a re-inflated tree with the buffer carried from the outgoing one.
     * Never reads the file or the draft - that would drop unsaved text - and never rebaselines the
     * dirty tracker, so text that was unsaved before the rotation stays marked unsaved.
     */
    fun reattachEditor(text: String, selectionStart: Int, selectionEnd: Int) {
        Timber.d("S4714: reattachEditor re-opens the editor with the carried buffer")
        safeViews.etTextContent.setText(text)
        showEditorViews()
        editContentFlow.value = text
        attachEditorHelpers(getCurrentFile()?.path.orEmpty())
        safeViews.etTextContent.setSelection(
            selectionStart.coerceIn(0, text.length),
            selectionEnd.coerceIn(0, text.length),
        )
    }

    private fun showEditorViews() {
        Timber.d("S4722: showEditorViews editor panel shown below the file-name plate inset")
        safeViews.textScrollView.isVisible = false
        safeViews.btnCopyTextCmd.isVisible = false
        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false
        safeViews.textEditContainer.isVisible = true
        bindImeInsets()
        safeViews.etTextContent.requestFocus()
        onEditorVisibilityChanged()
    }

    /**
     * Both hosts draw edge-to-edge, so the window is never resized for the keyboard: without this
     * padding the lower part of the editor stays behind it and cannot be scrolled to.
     */
    private fun bindImeInsets() {
        val container = safeViews.textEditContainer
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val keyboardBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // The host already reserves the system-bar gap below the content area; the keyboard inset spans it too.
            val barsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            // That gap is the host's only bottom reservation, so a cutout deeper than it is kept clear here (Rule 17).
            val cutoutBottom = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom
            val overlap = (maxOf(keyboardBottom, cutoutBottom) - barsBottom).coerceAtLeast(0)
            if (view.paddingBottom != overlap) {
                Timber.d("S4862: editor bottom padding follows the keyboard")
                view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, overlap)
                if (overlap > 0) revealCursorLine()
            }
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun unbindImeInsets() {
        val container = safeViews.textEditContainer
        ViewCompat.setOnApplyWindowInsetsListener(container, null)
        container.setPadding(container.paddingLeft, container.paddingTop, container.paddingRight, 0)
    }

    /** The shrunk scroll area keeps its offset, so the caret line may land under the keyboard; scroll it back. */
    private fun revealCursorLine() {
        val editText = safeViews.etTextContent
        editText.post {
            val layout = editText.layout ?: return@post
            val line = layout.getLineForOffset(editText.selectionEnd.coerceAtLeast(0))
            val lineTop = layout.getLineTop(line) + editText.totalPaddingTop
            val lineBottom = layout.getLineBottom(line) + editText.totalPaddingTop
            editText.requestRectangleOnScreen(Rect(0, lineTop, editText.width, lineBottom))
        }
    }

    /** Wires everything an open editor needs onto the current views; the caller has already set the text. */
    private fun attachEditorHelpers(filePath: String) {
        safeViews.etTextContent.removeTextChangedListener(dirtyTextWatcher)
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
            ).also {
                it.attach()
                it.reset()
                TextFontScalePrefs.get(context, filePath)?.let { remembered ->
                    Timber.d("S4863: editor adopts the remembered font size")
                    it.lockToSize(remembered)
                }
            }
        )
        getUndoRedoManager()?.detach() // M-10 fix.
        setUndoRedoManager(
            TextUndoRedoManager(safeViews.etTextContent) { canUndo, canRedo ->
                safeViews.btnUndo.alpha = if (canUndo) 1f else 0.3f
                safeViews.btnUndo.isEnabled = canUndo
                safeViews.btnRedo.alpha = if (canRedo) 1f else 0.3f
                safeViews.btnRedo.isEnabled = canRedo
            }.also { it.attach() }
        )
        getAutoSaveManager()?.startAutoSave(safeViews.etTextContent, filePath)
        findReplaceManager.setupCursorPositionTracking()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInputImplicitly(safeViews.etTextContent)
    }

    fun exitEditMode() {
        editorSession++
        pendingEnterJob?.cancel()
        pendingEnterJob = null
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(safeViews.etTextContent.windowToken, 0)
        releaseEditorHelpers()
        actionPanelManager.onExitEditMode()
        findReplaceManager.closeFindPanel()
        safeViews.textEditContainer.isVisible = false
        safeViews.textScrollView.isVisible = true
        safeViews.btnCopyTextCmd.isVisible = true
        safeViews.btnEditTextCmd.isVisible = true
        safeViews.btnSearchTextCmd.isVisible = true
        onEditorVisibilityChanged()
    }

    fun saveContentDocument(canWrite: Boolean, allowCopy: Boolean, onSuccess: () -> Unit): Boolean {
        val source = getCurrentFile()?.takeIf { it.path.startsWith("content://") } ?: return false
        if (documentSaveJob?.isActive != true) {
            documentSaveJob = launchDocumentSave(source, canWrite, allowCopy, onSuccess)
        }
        return true
    }

    private fun launchDocumentSave(
        source: MediaFile,
        canWrite: Boolean,
        allowCopy: Boolean,
        onSuccess: () -> Unit,
    ): Job {
        val content = safeViews.etTextContent.text.toString()
        val pager = getTextFilePager()
        val canOverwrite = canWrite && source.attributes?.readOnly != true
        val session = editorSession
        val isCurrent = {
            editorSession == session && getCurrentFile() === source && safeViews.textEditContainer.isVisible
        }
        return coroutineScope.launch {
            val saved = documentSaveManager.save(source, content, pager, canOverwrite, allowCopy, isCurrent, showError)
            Timber.d("S4839: SAF text commit finished, outcome=%s", saved)
            if (saved == null) return@launch
            if (!isCurrent()) return@launch
            if (saved == TextDocumentSaveManager.Destination.ORIGINAL) {
                applyDocumentSave(source, content, isCurrent)
            }
            if (!isCurrent()) return@launch
            Toast.makeText(context, R.string.toast_text_saved, Toast.LENGTH_SHORT).show()
            if (safeViews.etTextContent.text.toString() == content) onSuccess()
        }
    }

    private suspend fun applyDocumentSave(source: MediaFile, content: String, isCurrent: () -> Boolean) {
        val showLineNumbers = withContext(ioDispatcher) {
            settingsRepository.getSettings().first().showTextLineNumbers
        }
        if (!isCurrent()) return
        setOriginalTextWithoutNumbers(content)
        safeViews.tvTextContent.text = applyLineNumbers(content, showLineNumbers, 1)
        dirtyTracker.rebaseline(content)
        if (safeViews.etTextContent.text.toString() != content) return
        getAutoSaveManager()?.let { autoSave ->
            autoSave.stopAutoSave()
            autoSave.deleteDraft(source.path)
            if (isCurrent()) autoSave.startAutoSave(safeViews.etTextContent, source.path)
        }
    }

    fun saveEditedText(onSuccess: () -> Unit) {
        val newText = safeViews.etTextContent.text.toString()
        val fileToSave = getCurrentFile() ?: run {
            showError(context.getString(R.string.text_file_not_found))
            return
        }
        setTextSaveSpinner(true)
        coroutineScope.launch(ioDispatcher) {
            try {
                val localFile = networkFileManager.prepareFileForWrite(fileToSave)
                if (localFile == null) {
                    withContext(Dispatchers.Main) {
                        setTextSaveSpinner(false)
                        showError(context.getString(R.string.text_file_save_failed))
                    }
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
                }
                val settings = settingsRepository.getSettings().first() // C-3 fix: read settings on IO, not Main.
                withContext(Dispatchers.Main) {
                    if (getCurrentFile()?.path != fileToSave.path ||
                        safeViews.etTextContent.text.toString() != newText
                    ) {
                        return@withContext
                    }
                    setOriginalTextWithoutNumbers(newText)
                    safeViews.tvTextContent.text = applyLineNumbers(newText, settings.showTextLineNumbers, 1)
                    getAutoSaveManager()?.stopAutoSaveAndDeleteDraft()
                    exitEditMode()
                    Toast.makeText(context, R.string.toast_text_saved, Toast.LENGTH_SHORT).show()
                    onSuccess()
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
