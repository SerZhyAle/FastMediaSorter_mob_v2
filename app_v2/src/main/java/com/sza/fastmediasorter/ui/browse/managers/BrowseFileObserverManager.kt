package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.data.observer.MediaFileObserver
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.ui.browse.BrowseState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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
 * Extracted from BrowseViewModel (Wave 1 decomposition - IV.1).
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
    private val onSortFiles: (files: List<MediaFile>, sortMode: SortMode) -> List<MediaFile>,
    // S0242 Phase 05 - Route LOCAL FileObserver events through the journal so Reconciler
    // becomes the single reader of all source-mutation signals. resourceId is captured at
    // construction time because the manager is owned by a per-window BrowseViewModel
    // bound to one resource for its entire lifecycle (matches BrowseLoadingAuxManager).
    private val resourceId: Long,
    private val mutationJournal: MutationJournal,
    private val pathNormalizer: PathNormalizer
) {
    // ── State ────────────────────────────────────────────────────────────────

    private var fileObserver: MediaFileObserver? = null
    private var reloadDebounceJob: Job? = null
    private var pendingRenameFrom: String? = null
    private var pendingRenameTo: String? = null
    private var pendingRenameJob: Job? = null

    // Counter-based guard: allows nested/concurrent callers without race conditions.
    // Each call to setIgnoringFileChanges(true) increments; setIgnoringFileChanges(false) decrements.
    // ignoringFileChanges == true when count > 0.
    private val ignoringCount = AtomicInteger(0)

    val ignoringFileChanges: Boolean get() = ignoringCount.get() > 0

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
                        if (TrashFolderContract.matchesTrashSegment(fileName)) {
                            Timber.d("FileObserver: ignoring trash container deletion: $fileName")
                            return
                        }
                        Timber.i("FileObserver: external file deleted: $fileName")
                        val currentResource = stateFlow.value.resource ?: return
                        val fullPath = File(currentResource.path, fileName).absolutePath
                        // S0242 Phase 05 - Journal the deletion so the Reconciler reapplies it
                        // on next onResume if the user navigates away before the live UI removal
                        // takes effect (or if multiple Browse instances share the resource).
                        // The immediate onRemoveFiles call stays for the live-view UX; Reconciler
                        // becomes a no-op when the visible list already lacks the path.
                        recordDelete(fullPath)
                        onRemoveFiles(listOf(fullPath))
                    }

                    override fun onFileCreated(fileName: String) {
                        if (ignoringFileChanges) {
                            Timber.d("FileObserver: ignoring created event (programmatic): $fileName")
                            return
                        }
                        if (TrashFolderContract.matchesTrashSegment(fileName)) {
                            Timber.d("FileObserver: ignoring trash container creation: $fileName")
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
                                    // S0242 Phase 05 - Both old and new paths fall within the
                                    // same resource root (FileObserver fires only for the watched
                                    // directory), so it's a same-resource Move per Mutation.Move
                                    // semantics. Live in-memory rename stays for UX.
                                    val currentResource = stateFlow.value.resource ?: return@launch
                                    val oldPath = File(currentResource.path, from).absolutePath
                                    val newPath = File(currentResource.path, to).absolutePath
                                    recordMoveWithinResource(oldPath, newPath)
                                    handleFileRename(from, to)
                                }
                                from != null && to == null -> {
                                    val currentResource = stateFlow.value.resource ?: return@launch
                                    val filePath = File(currentResource.path, from)
                                    if (!filePath.exists()) {
                                        Timber.i("FileObserver: file deleted/moved to trash: $from")
                                        // S0242 Phase 05 - Journal the disappearance; live
                                        // onRemoveFiles preserves immediate UI feedback.
                                        recordDelete(filePath.absolutePath)
                                        onRemoveFiles(listOf(filePath.absolutePath))
                                    } else {
                                        Timber.w("FileObserver: file moved externally: $from - full reload")
                                        // S0242 Phase 05 - File left the watched resource (paired
                                        // 'to' event never arrived but the file is gone from this
                                        // dir on resume / next probe). Record the delete; the live
                                        // reload still runs because we don't know the destination.
                                        recordDelete(filePath.absolutePath)
                                        scheduleReload()
                                    }
                                }
                                from == null && to != null -> {
                                    Timber.i("FileObserver: file moved in: $to - full reload")
                                    // S0242 §6 Item 5 - the journal does not model `Add`. New
                                    // files in the watched directory keep the legacy reload path.
                                    scheduleReload()
                                }
                                else -> {
                                    Timber.w("FileObserver: incomplete move event (from=$from, to=$to) - full reload")
                                    // S0242 Phase 05 - Insufficient information to journal;
                                    // fall back to the legacy reload so the list catches up.
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

    /** Increment or decrement the suppression counter. */
    fun setIgnoringFileChanges(ignoring: Boolean) {
        if (ignoring) ignoringCount.incrementAndGet() else ignoringCount.decrementAndGet()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * S0242 Phase 05 - Append a [Mutation.Delete] entry to the shared journal so
     * `BrowseReconcilerManager` reapplies it on next `onResume`. Synchronous: the journal
     * implementation is thread-safe and writes only to an in-memory map.
     *
     * FileObserver fires only for LOCAL resources (Android `android.os.FileObserver`),
     * so [ResourceType.LOCAL] is hard-coded for canonicalization.
     */
    private fun recordDelete(rawPath: String) {
        val canonical = pathNormalizer.canonical(rawPath, ResourceType.LOCAL)
        mutationJournal.record(
            Mutation.Delete(
                resourceId = resourceId,
                canonicalPath = canonical,
                opId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * S0242 Phase 05 - Append a same-resource [Mutation.Move] entry. Both paths fall
     * within the watched directory's resource, so `srcResourceId == dstResourceId == resourceId`.
     */
    private fun recordMoveWithinResource(oldRawPath: String, newRawPath: String) {
        val oldCanonical = pathNormalizer.canonical(oldRawPath, ResourceType.LOCAL)
        val newCanonical = pathNormalizer.canonical(newRawPath, ResourceType.LOCAL)
        mutationJournal.record(
            Mutation.Move(
                resourceId = resourceId,
                srcResourceId = resourceId,
                dstResourceId = resourceId,
                oldCanonicalPath = oldCanonical,
                newCanonicalPath = newCanonical,
                opId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis()
            )
        )
    }

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
                Timber.w("FileObserver.handleFileRename: '$oldName' not found - falling back to reload")
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
