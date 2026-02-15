package com.sza.fastmediasorter.ui.player.helpers

import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Auto-save manager for text editor drafts.
 * Periodically saves EditText content to a draft file in temp/ directory.
 * Restores draft on re-entering edit mode if available.
 *
 * Draft files are stored as: temp/draft_{md5(filePath)}.txt
 */
class TextEditorAutoSaveManager(
    private val tempDir: File,
    private val coroutineScope: CoroutineScope
) {

    companion object {
        private const val AUTO_SAVE_INTERVAL_MS = 15_000L // 15 seconds
        private const val DRAFT_PREFIX = "draft_"
        private const val DRAFT_SUFFIX = ".txt"
    }

    private var autoSaveJob: Job? = null
    private var currentDraftFile: File? = null
    @Volatile  // Accessed from multiple threads (C-4 fix)
    private var lastSavedHash: Int = 0

    /**
     * Start auto-saving the EditText content for the given source file.
     * @param editText The EditText to monitor
     * @param sourceFilePath Original file path (used to generate draft name)
     */
    fun startAutoSave(editText: EditText, sourceFilePath: String) {
        stopAutoSave()
        currentDraftFile = getDraftFile(sourceFilePath)
        lastSavedHash = editText.text.hashCode()

        autoSaveJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(AUTO_SAVE_INTERVAL_MS)
                if (!isActive) break

                val text = withContext(Dispatchers.Main) {
                    editText.text?.toString()
                } ?: continue

                val currentHash = text.hashCode()
                if (currentHash != lastSavedHash && isActive) { // Check isActive before saving (C-4 fix)
                    saveDraft(text)
                    lastSavedHash = currentHash
                }
            }
        }
        Timber.d("TextEditorAutoSave: Started for $sourceFilePath")
    }

    /**
     * Stop auto-saving and optionally delete the draft.
     * @param deleteDraft If true, removes the draft file (e.g., after successful save)
     */
    fun stopAutoSave(deleteDraft: Boolean = false) {
        autoSaveJob?.cancel()
        autoSaveJob = null
        if (deleteDraft) {
            currentDraftFile?.let { draft ->
                if (draft.exists()) {
                    draft.delete()
                    Timber.d("TextEditorAutoSave: Draft deleted: ${draft.name}")
                }
            }
        }
        currentDraftFile = null
        lastSavedHash = 0
    }

    /**
     * Check if a draft exists for the given source file.
     */
    fun hasDraft(sourceFilePath: String): Boolean {
        return getDraftFile(sourceFilePath).exists()
    }

    /**
     * Restore draft content for the given source file.
     * @return Draft text or null if no draft exists
     */
    fun restoreDraft(sourceFilePath: String): String? {
        val draftFile = getDraftFile(sourceFilePath)
        if (!draftFile.exists()) return null
        return try {
            val text = draftFile.readText()
            Timber.d("TextEditorAutoSave: Restored draft (${text.length} chars) from ${draftFile.name}")
            text
        } catch (e: Exception) {
            Timber.e(e, "TextEditorAutoSave: Failed to restore draft")
            null
        }
    }

    /**
     * Force save current content immediately.
     */
    fun forceSave(text: String) {
        coroutineScope.launch(Dispatchers.IO) {
            saveDraft(text)
        }
    }

    /**
     * Delete draft for a given source file (e.g., after successful save).
     */
    fun deleteDraft(sourceFilePath: String) {
        val draftFile = getDraftFile(sourceFilePath)
        if (draftFile.exists()) {
            draftFile.delete()
            Timber.d("TextEditorAutoSave: Draft deleted for $sourceFilePath")
        }
    }

    private fun saveDraft(text: String) {
        val draftFile = currentDraftFile ?: return
        try {
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            draftFile.writeText(text)
            Timber.d("TextEditorAutoSave: Saved draft (${text.length} chars) to ${draftFile.name}")
        } catch (e: Exception) {
            Timber.e(e, "TextEditorAutoSave: Failed to save draft")
        }
    }

    private fun getDraftFile(sourceFilePath: String): File {
        val hash = sourceFilePath.hashCode().toUInt().toString(16)
        return File(tempDir, "$DRAFT_PREFIX${hash}$DRAFT_SUFFIX")
    }
}
