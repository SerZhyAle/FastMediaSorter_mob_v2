package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
/**
 * S0189: orchestrates the save-with-rename dialog flow for text note editing.
 *
 * Shows [TextNoteSaveDialog], dispatches the save coroutine, surfaces toasts, and
 * invokes [afterSave] on success. Does NOT touch UI state beyond toasts - the caller
 * controls post-save navigation (exit edit mode, share sheet, etc.).
 *
 * Constructed manually by [PlayerViewerFactory] (TextViewerManager is not Hilt-managed).
 */
class TextEditorSaveFlow(
    private val context: Context,
    // S1195: an operation rather than the use case, so an Activity host can route it through its ViewModel.
    private val saveTextNote: suspend (
        localFile: File,
        name: String,
        content: String,
    ) -> Result<SaveTextNoteUseCase.SaveOutcome>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Start the save flow: show the rename dialog, save on confirm, surface result toasts.
     *
     * @param currentLocalFile  The backing local file (staging or real).
     * @param currentName       Default filename shown in the dialog.
     * @param currentContent    Current editor content to persist.
     * @param afterSave         Called on the Main thread after a successful save.
     * @param onCancel          Called if the user dismisses the dialog.
     */
    fun commit(
        currentLocalFile: File,
        currentName: String,
        currentContent: String,
        afterSave: (SaveTextNoteUseCase.SaveOutcome) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        TextNoteSaveDialog.show(
            context = context,
            defaultName = currentName,
            onConfirm = { chosen ->
                scope.launch(ioDispatcher) {
                    val result = saveTextNote(currentLocalFile, chosen, currentContent)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { outcome ->
                            showSuccessToasts(outcome)
                            afterSave(outcome)
                        }
                        result.onFailure { e ->
                            Timber.e(e, "TextEditorSaveFlow save failed")
                            Toast.makeText(context, R.string.text_note_save_error_network, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            onCancel = onCancel
        )
    }

    private fun showSuccessToasts(outcome: SaveTextNoteUseCase.SaveOutcome) {
        if (outcome.renamedDueToConflict) {
            Toast.makeText(
                context,
                context.getString(R.string.text_note_save_renamed_suffix, outcome.finalName),
                Toast.LENGTH_LONG
            ).show()
        }

        if (outcome.remoteFailureMessage != null) {
            Toast.makeText(context, R.string.text_note_save_error_network, Toast.LENGTH_LONG).show()
        } else if (outcome.isLocalSaveOnly) {
            Toast.makeText(context, R.string.text_note_save_success_local, Toast.LENGTH_SHORT).show()
        } else {
            val destination = outcome.finalPath.substringBeforeLast("/")
            Toast.makeText(
                context,
                context.getString(R.string.text_note_save_success_remote, destination),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
