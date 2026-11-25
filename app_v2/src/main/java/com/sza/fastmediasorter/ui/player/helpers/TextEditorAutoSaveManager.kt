package com.sza.fastmediasorter.ui.player.helpers

import android.widget.EditText
import kotlinx.coroutines.CoroutineDispatcher
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
    private val coroutineScope: CoroutineScope,
    // Defaulted because the unit test builds it without one, so its draft writes race on the real IO pool.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val AUTO_SAVE_INTERVAL_MS = 15_000L // 15 seconds
        private const val DRAFT_PREFIX = "draft_"
        private const val DRAFT_SUFFIX = ".txt"
    }

    private var autoSaveJob: Job? = null

    // Two locks so a main-thread start/stop never waits for a draft write in flight: stateLock
    // guards only the fields below and is never held across disk IO, ioLock serialises every draft
    // file access. Acquisition order is always ioLock -> stateLock, never the reverse.
    private val stateLock = Any()
    private val ioLock = Any()
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
        synchronized(stateLock) {
            currentDraftFile = getDraftFile(sourceFilePath)
            lastSavedHash = editText.text.hashCode()
        }

        autoSaveJob = coroutineScope.launch(ioDispatcher) {
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
     * Stop auto-saving and keep the draft file. Touches no disk and never waits for a write in
     * flight, so it is safe on the main thread.
     */
    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        synchronized(stateLock) {
            currentDraftFile = null
            lastSavedHash = 0
        }
    }

    /**
     * Stop auto-saving and delete the draft (e.g., after successful save).
     * The delete and the state reset share one [ioLock] hold, and [saveDraft] reads the current
     * draft inside that lock, so a queued save sees no current draft and cannot recreate the file.
     */
    suspend fun stopAutoSaveAndDeleteDraft() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        withContext(ioDispatcher) {
            synchronized(ioLock) {
                val draft = synchronized(stateLock) {
                    val current = currentDraftFile
                    currentDraftFile = null
                    lastSavedHash = 0
                    current
                }
                if (draft != null && draft.exists()) {
                    draft.delete()
                    Timber.d("TextEditorAutoSave: Draft deleted: ${draft.name}")
                }
            }
        }
    }

    /**
     * Check if a draft exists for the given source file.
     */
    suspend fun hasDraft(sourceFilePath: String): Boolean = withContext(ioDispatcher) {
        synchronized(ioLock) {
            getDraftFile(sourceFilePath).exists()
        }
    }

    /**
     * Restore draft content for the given source file.
     * @return Draft text or null if no draft exists
     */
    fun restoreDraft(sourceFilePath: String): String? {
        synchronized(ioLock) {
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
    }

    /**
     * Force save current content immediately.
     */
    fun forceSave(text: String) {
        coroutineScope.launch(ioDispatcher) {
            saveDraft(text)
        }
    }

    /**
     * Delete draft for a given source file (e.g., after successful save).
     */
    suspend fun deleteDraft(sourceFilePath: String) {
        withContext(ioDispatcher) {
            synchronized(ioLock) {
                val draftFile = getDraftFile(sourceFilePath)
                if (draftFile.exists()) {
                    draftFile.delete()
                    Timber.d("TextEditorAutoSave: Draft deleted for $sourceFilePath")
                }
                synchronized(stateLock) {
                    if (currentDraftFile?.absolutePath == draftFile.absolutePath) {
                        currentDraftFile = null
                        lastSavedHash = 0
                    }
                }
            }
        }
    }

    private fun saveDraft(text: String) {
        synchronized(ioLock) {
            val draftFile = synchronized(stateLock) { currentDraftFile } ?: return
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
    }

    private fun getDraftFile(sourceFilePath: String): File {
        val hash = sourceFilePath.hashCode().toUInt().toString(16)
        return File(tempDir, "$DRAFT_PREFIX${hash}$DRAFT_SUFFIX")
    }
}
