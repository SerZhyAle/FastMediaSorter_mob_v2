package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.data.observer.MediaFileObserver
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Manages [MediaFileObserver] lifecycle for local resource monitoring.
 *
 * Responsibilities:
 * - Start / stop an OS-level FileObserver for the current LOCAL resource directory.
 * - Debounce rapid change events to avoid excessive reloads.
 * - Apply in-memory renames without a full rescan.
 * - Expose [ignoringFileChanges] flag so programmatic file operations can suppress
 *   spurious observer callbacks.
 *
 * Extracted from BrowseViewModel (Wave 1 decomposition — IV.1).
 */
class BrowseFileObserverManager(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<BrowseState>,
    private val updateState: ((BrowseState) -> BrowseState) -> Unit,
    /** Called when the observer detects a file deletion. */
    private val onRemoveFiles: (paths: List<String>) -> Unit,
    /** Called when the observer detects a change requiring a full reload. */
    private val onReloadFiles: () -> Unit,
    /**
     * Called to sort files in-memory after a rename.
     * Equivalent to `sortFiles(files, sortMode, forceSort = true)`.
     */
    private val onSortFiles: (files: List<MediaFile>, sortMode: SortMode) -> List<MediaFile>
) {
    // ── State ────────────────────────────────────────────────────────────────

    private var fileObserver: MediaFileObserver? = null
    private var reloadDebounceJob: Job? = null
    private var pendingRenameFrom: String? = null
    private var pendingRenameTo: String? = null
    private var pendingRenameJob: Job? = null

    /**
     * When `true`, all FileObserver callbacks are silently ignored.
     * Set to `true` before programmatic file operations (delete, move, rename) and
     * back to `false` when complete to avoid spurious UI reloads.
     */
    @Volatile
    var ignoringFileChanges: Boolean = false
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /** Start observing the current resource's root directory (LOCAL resources only). */
    fun start() {
        val resource = stateFlow.value.resource ?: return
        if (resource.type != ResourceType.LOCAL) return

        stop()

        try {
            fileObserver = MediaFileObserver(
                path = resource.path,
                listener = object : MediaFileObserver.FileChangeListener {
                    override fun onFileDeleted(fileName: String) {
                        if (ignoringFileChanges) {
                            Timber.d("FileObserver: ignoring deleted event (programmatic): $fileName")
                            return
                        }
                        if (fileName.startsWith(".trash_")) {
                            Timber.d("FileObserver: ignoring trash folder deletion: $fileName")
                            return
                        }
                        Timber.i("FileObserver: external file deleted: $fileName")
                        val currentResource = stateFlow.value.resource ?: return
                        val fullPath = File(currentResource.path, fileName).absolutePath
                        onRemoveFiles(listOf(fullPath))
                    }

                    override fun onFileCreated(fileName: String) {
                        if (ignoringFileChanges) {
                            Timber.d("FileObserver: ignoring created event (programmatic): $fileName")
                            return
                        }
                        if (fileName.startsWith(".trash_")) {
                            Timber.d("FileObserver: ignoring trash folder creation: $fileName")
                            return
                        }
                        Timber.i("FileObserver: external file created: $fileName")
                        scheduleReload()
                    }

                    override fun onFileMoved(fromName: String?, toName: String?) {
                        if (ignoringFileChanges) {
                            Timber.d("FileObserver: ignoring moved event (programmatic): from=$fromName, to=$toName")
                            return
                        }
                        Timber.i("FileObserver: external file moved: from=$fromName, to=$toName")

                        // Rename generates 2 events: (from=X, to=null) then (from=null, to=Y).
                        // Collect both parts before processing.
                        pendingRenameJob?.cancel()
                        if (fromName != null) pendingRenameFrom = fromName
                        if (toName != null) pendingRenameTo = toName

                        pendingRenameJob = scope.launch {
                            delay(50) // wait for the paired event
                            val from = pendingRenameFrom
                            val to = pendingRenameTo
                            pendingRenameFrom = null
                            pendingRenameTo = null

                            when {
                                from != null && to != null -> {
                                    Timber.i("FileObserver: complete rename '$from' → '$to'")
                                    handleFileRename(from, to)
                                }
                                from != null && to == null -> {
                                    val currentResource = stateFlow.value.resource ?: return@launch
                                    val filePath = File(currentResource.path, from)
                                    if (!filePath.exists()) {
                                        Timber.i("FileObserver: file deleted/moved to trash: $from")
                                        onRemoveFiles(listOf(filePath.absolutePath))
                                    } else {
                                        Timber.w("FileObserver: file moved externally: $from — full reload")
                                        scheduleReload()
                                    }
                                }
                                from == null && to != null -> {
                                    Timber.i("FileObserver: file moved in: $to — full reload")
                                    scheduleReload()
                                }
                                else -> {
                                    Timber.w("FileObserver: incomplete move event (from=$from, to=$to) — full reload")
                                    scheduleReload()
                                }
                            }
                        }
                    }

                    override fun onFileModified(fileName: String) {
                        Timber.d("FileObserver: external file modified: $fileName")
                        // Skip reload on modification to avoid too many refreshes
                    }
                }
            )
            fileObserver?.startWatching()
            Timber.d("FileObserver: started for '${resource.path}'")
        } catch (e: Exception) {
            Timber.e(e, "FileObserver: failed to start for '${resource.path}'")
        }
    }

    /** Stop observing and clean up all pending jobs. */
    fun stop() {
        fileObserver?.stopWatching()
        fileObserver = null
        reloadDebounceJob?.cancel()
        reloadDebounceJob = null
        pendingRenameJob?.cancel()
        pendingRenameJob = null
        pendingRenameFrom = null
        pendingRenameTo = null
        Timber.d("FileObserver: stopped")
    }

    /** Set/clear the programmatic-change suppression flag. */
    fun setIgnoringFileChanges(ignoring: Boolean) {
        ignoringFileChanges = ignoring
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Debounce reloadFiles() calls triggered by rapid FileObserver events.
     */
    private fun scheduleReload() {
        reloadDebounceJob?.cancel()
        reloadDebounceJob = scope.launch {
            delay(300)
            Timber.d("FileObserver: debounced reload triggered")
            onReloadFiles()
        }
    }

    /**
     * Apply an in-memory rename to avoid a full rescan.
     * Falls back to [onReloadFiles] if the file is not found in the current list.
     */
    private fun handleFileRename(oldName: String, newName: String) {
        scope.launch {
            val currentState = stateFlow.value
            val currentList = currentState.mediaFiles

            val fileIndex = currentList.indexOfFirst { it.name == oldName }
            if (fileIndex == -1) {
                Timber.w("FileObserver.handleFileRename: '$oldName' not found — falling back to reload")
                onReloadFiles()
                return@launch
            }

            val oldFile = currentList[fileIndex]
            val resource = currentState.resource ?: return@launch
            val newPath = File(resource.path, newName).absolutePath
            val updatedFile = oldFile.copy(name = newName, path = newPath)

            Timber.i("FileObserver.handleFileRename: '$oldName' → '$newName' (index=$fileIndex)")

            val updatedList = currentList.toMutableList().apply { set(fileIndex, updatedFile) }
            val sortedList = if (currentState.sortMode != SortMode.NAME_ASC) {
                onSortFiles(updatedList, currentState.sortMode)
            } else {
                updatedList
            }

            updateState { it.copy(mediaFiles = sortedList) }
            Timber.i("FileObserver.handleFileRename: list updated, total=${sortedList.size}")
        }
    }
}
