package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase
import com.sza.fastmediasorter.ui.editor.actions.EditorActionCallbacks
import java.io.File

/** Editor-panel callbacks for [TextViewerManager]. Extracted to keep the host class under the 1000-LOC budget. */
internal class TextEditorActionPanelCallbacks(
    private val safeViews: PlayerBindingSafeViews,
    private val getSaveFlow: () -> TextEditorSaveFlow?,
    private val getCurrentLocalFile: () -> File?,
    private val getTextNoteStagingRegistry: () -> LocalStagingRegistry?,
    private val saveDialogDefaultName: (File) -> String,
    private val cacheNewlySavedNote: (SaveTextNoteUseCase.SaveOutcome, String) -> Unit,
    private val rebaselineDirtyTracker: (String) -> Unit,
    private val isDirty: () -> Boolean,
    private val saveEditedText: () -> Unit,
    // S0459: opens the unified «Send to..» menu for the supplied text (post-save). The host resolves
    // the FragmentActivity, settings, and SendToMenuManager - this class stays UI-state-agnostic.
    private val sendTo: (text: String) -> Unit,
    private val openCalculator: (String) -> Unit,
    private val finishActivity: () -> Unit,
    private val exitEditMode: () -> Unit,
) {
    fun build(): EditorActionCallbacks = EditorActionCallbacks(
        onSave = ::onSave,
        onSaveAndClose = ::onSaveAndClose,
        onSendTo = ::onSendTo,
        onOpenCalculator = ::onOpenCalculator,
        onCancel = ::onCancel,
    )

    private fun onSave() {
        val flow = getSaveFlow()
        val localFile = getCurrentLocalFile()
        val capturedContent = safeViews.etTextContent.text.toString()
        if (flow != null && localFile != null) {
            flow.commit(
                currentLocalFile = localFile,
                currentName = saveDialogDefaultName(localFile),
                currentContent = capturedContent,
                afterSave = { outcome ->
                    cacheNewlySavedNote(outcome, capturedContent)
                    // S0189: reset dirty-state - Save & Close on a clean buffer must skip the redundant re-save (orphans a file in staging dir otherwise).
                    rebaselineDirtyTracker(capturedContent)
                },
            )
        } else {
            saveEditedText()
            rebaselineDirtyTracker(capturedContent)
        }
    }

    private fun onSaveAndClose() {
        val flow = getSaveFlow()
        val localFile = getCurrentLocalFile()
        val capturedContent = safeViews.etTextContent.text.toString()
        // S0189: clean buffer (user did Save then Save&Close) - skip duplicate save and just return to Browse.
        if (!isDirty()) {
            finishActivity()
            return
        }
        if (flow != null && localFile != null) {
            flow.commit(
                currentLocalFile = localFile,
                currentName = saveDialogDefaultName(localFile),
                currentContent = capturedContent,
                afterSave = { outcome ->
                    cacheNewlySavedNote(outcome, capturedContent)
                    rebaselineDirtyTracker(capturedContent)
                    finishActivity()
                },
            )
        } else {
            saveEditedText()
            rebaselineDirtyTracker(capturedContent)
            finishActivity()
        }
    }

    // S0459: unified outbound action. Save (in place / staged-rename) exactly as before, then open the
    // «Send to..» menu with the saved text; receivers (system Share, Keep-text, Email, ..) self-gate.
    private fun onSendTo() {
        val flow = getSaveFlow()
        val localFile = getCurrentLocalFile()
        val capturedContent = safeViews.etTextContent.text.toString()
        if (flow != null && localFile != null) {
            flow.commit(
                currentLocalFile = localFile,
                currentName = saveDialogDefaultName(localFile),
                currentContent = capturedContent,
                afterSave = { outcome ->
                    cacheNewlySavedNote(outcome, capturedContent)
                    rebaselineDirtyTracker(capturedContent)
                    sendTo(capturedContent)
                },
            )
        } else {
            saveEditedText()
            rebaselineDirtyTracker(capturedContent)
            sendTo(capturedContent)
        }
    }

    private fun onOpenCalculator() {
        val editable = safeViews.etTextContent.text
        val start = safeViews.etTextContent.selectionStart
        val end = safeViews.etTextContent.selectionEnd
        val selectedText = if (start >= 0 && end >= 0 && start != end) {
            val from = minOf(start, end).coerceIn(0, editable.length)
            val to = maxOf(start, end).coerceIn(0, editable.length)
            editable.substring(from, to)
        } else {
            ""
        }
        openCalculator(selectedText)
    }

    private fun onCancel() {
        // S0189: deferred new-note that auto-save flushed to disk - delete the file and drop the registry entry so Cancel leaves no trace.
        val localFile = getCurrentLocalFile()
        val registry = getTextNoteStagingRegistry()
        if (localFile != null && registry != null) {
            val stagedNote = registry.lookup(localFile)
            if (stagedNote != null) {
                if (localFile.exists()) localFile.delete()
                registry.unregister(localFile)
            }
        }
        exitEditMode()
    }
}
