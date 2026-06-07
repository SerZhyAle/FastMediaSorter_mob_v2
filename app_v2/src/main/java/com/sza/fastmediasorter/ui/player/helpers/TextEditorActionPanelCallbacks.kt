package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.SharePayload
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase
import com.sza.fastmediasorter.ui.editor.actions.EditorActionCallbacks
import com.sza.fastmediasorter.util.GoogleKeepAvailabilityChecker
import java.io.File

/** Editor-panel callbacks for [TextViewerManager]. Extracted to keep the host class under the 1000-LOC budget. */
internal class TextEditorActionPanelCallbacks(
    private val context: Context,
    private val safeViews: PlayerBindingSafeViews,
    private val keepChecker: GoogleKeepAvailabilityChecker,
    private val getSaveFlow: () -> TextEditorSaveFlow?,
    private val getCurrentLocalFile: () -> File?,
    private val getTextNoteStagingRegistry: () -> LocalStagingRegistry?,
    private val saveDialogDefaultName: (File) -> String,
    private val cacheNewlySavedNote: (SaveTextNoteUseCase.SaveOutcome, String) -> Unit,
    private val rebaselineDirtyTracker: (String) -> Unit,
    private val isDirty: () -> Boolean,
    private val saveEditedText: () -> Unit,
    private val openCalculator: (String) -> Unit,
    private val finishActivity: () -> Unit,
    private val exitEditMode: () -> Unit,
) {
    fun build(): EditorActionCallbacks = EditorActionCallbacks(
        onSave = ::onSave,
        onSaveAndClose = ::onSaveAndClose,
        onSaveAndSend = ::onSaveAndSend,
        onSendToKeep = ::onSendToKeep,
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

    private fun onSaveAndSend() {
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
                    val shareFile = File(outcome.finalPath)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                    SystemShareInvoker.invoke(
                        context = context,
                        payload = SharePayload.Text(content = capturedContent, streamUri = uri, grantReadPermission = true),
                        chooserTitle = context.getString(R.string.share),
                    )
                },
            )
        } else {
            saveEditedText()
            SystemShareInvoker.invoke(
                context = context,
                payload = SharePayload.Text(content = capturedContent),
                chooserTitle = context.getString(R.string.share),
            )
        }
    }

    private fun onSendToKeep() {
        val currentText = safeViews.etTextContent.text.toString()
        val keepPackage = keepChecker.resolveTargetPackage() ?: run {
            Toast.makeText(context, R.string.text_editor_keep_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val sent = SystemShareInvoker.invoke(
            context = context,
            payload = SharePayload.Text(content = currentText),
            preferredPackage = keepPackage,
        )
        if (!sent) {
            Toast.makeText(context, R.string.text_editor_keep_unavailable, Toast.LENGTH_SHORT).show()
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
