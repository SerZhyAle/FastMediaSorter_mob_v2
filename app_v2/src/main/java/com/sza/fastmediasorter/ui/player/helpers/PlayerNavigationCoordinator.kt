package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.render.RenderPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns navigation between files and the adjacent-file / lookahead computations that drive
 * the prefetch system. Extracted in Wave 4.2 of the giant-file decomposition.
 *
 * Also keeps the debounced + immediate `lastViewedFile` persistence because both share the
 * same cancellation job (`saveLastViewedFileJob`) and are only triggered from navigation.
 */
class PlayerNavigationCoordinator(
    private val scope: CoroutineScope,
    private val resourceRepository: ResourceRepository,
    private val stateFlow: StateFlow<PlayerViewModel.PlayerState>,
    private val updateState: ((PlayerViewModel.PlayerState) -> PlayerViewModel.PlayerState) -> Unit,
    private val saveResumeState: () -> Unit,
    private val clearPlaybackWatchdogs: () -> Unit = {},
    private val sendEvent: (PlayerViewModel.PlayerEvent) -> Unit = {}
) {

    private var saveLastViewedFileJob: Job? = null

    /** S1279: entries taken out optimistically, keyed by the source path the queue reports back. */
    private val pendingRemovals = mutableMapOf<String, RemovedFile>()

    fun syncAudioServiceIndex(serviceIndex: Int) {
        val files = stateFlow.value.files
        if (serviceIndex < 0 || serviceIndex >= files.size) return
        Timber.d("syncAudioServiceIndex: ${stateFlow.value.currentIndex} → $serviceIndex / ${files.size}")
        updateState { it.copy(currentIndex = serviceIndex) }
    }

    fun jumpToIndex(index: Int, manual: Boolean = false) {
        val currentState = stateFlow.value
        if (index !in currentState.files.indices) {
            Timber.d("jumpToIndex: ABORT invalid index=$index for size=${currentState.files.size}")
            return
        }
        if (index == currentState.currentIndex) {
            Timber.d("jumpToIndex: ABORT target index matches current index=$index")
            return
        }

        if (manual) clearPlaybackWatchdogs()

        Timber.d("jumpToIndex: index ${currentState.currentIndex} → $index / ${currentState.files.size}")

        updateState { it.copy(currentIndex = index) }
        saveResumeState()

        if (currentState.resource != null) {
            saveLastViewedFileDebounced(currentState.files[index].path)
        }
    }

    /**
     * S1279: take the current entry out of the navigated list after a player-initiated delete or
     * move, reporting what was removed so the caller can put it back if the queued operation later
     * fails.
     *
     * This REPLACES the advance those flows used to perform, it does not accompany it. Removing
     * the entry at `currentIndex` leaves that same index pointing at the following file, so calling
     * [nextFile] on top would step twice and silently skip a file after every sort action.
     *
     * An emptied list is deliberately left for `PlayerFileOperationEvent.Drained` to finish the
     * Activity, as it already does - finishing here would close the player before the queued
     * operation is known to have succeeded.
     */
    fun dropCurrentFile(sourcePath: String): Boolean {
        val currentState = stateFlow.value
        val index = currentState.currentIndex
        val file = currentState.files.getOrNull(index) ?: return false

        val updatedFiles = currentState.files.toMutableList().apply { removeAt(index) }
        // Wrap like the pre-queue delete path did: dropping the last entry lands on the first.
        val newIndex = if (index >= updatedFiles.size) 0 else index
        updateState {
            it.copy(
                files = updatedFiles,
                currentIndex = newIndex,
                shuffleIndices = shuffleIndicesWithout(it.shuffleIndices, index)
            )
        }
        if (updatedFiles.isNotEmpty()) saveResumeState()
        pendingRemovals[sourcePath] = RemovedFile(file, index)
        Timber.d("dropCurrentFile: removed $index, ${currentState.files.size} -> ${updatedFiles.size}, now $newIndex")
        return true
    }

    /** S1279: the queued operation succeeded, so there is nothing left to put back. */
    fun forgetDroppedFile(sourcePath: String) {
        pendingRemovals.remove(sourcePath)
    }

    /**
     * S1279: undo [dropCurrentFile] when the queued operation reports failure, so an operation
     * that never happened does not silently shrink the list.
     *
     * The viewer stays on whatever is on screen now rather than being yanked back to the restored
     * file - the user has already moved on, and the failure is reported by its own snackbar.
     */
    fun restoreDroppedFile(sourcePath: String) {
        val removed = pendingRemovals.remove(sourcePath) ?: return
        val currentState = stateFlow.value
        if (currentState.files.any { it.path == removed.file.path }) return

        val index = removed.index.coerceIn(0, currentState.files.size)
        val restored = currentState.files.toMutableList().apply { add(index, removed.file) }
        val shiftedIndex = if (currentState.currentIndex >= index) {
            currentState.currentIndex + 1
        } else {
            currentState.currentIndex
        }
        updateState {
            it.copy(
                files = restored,
                currentIndex = shiftedIndex.coerceIn(0, restored.size - 1),
                // Cleared rather than remapped: there is no defensible position for a re-inserted
                // file in an existing shuffle order, and nextFile rebuilds the order when it finds
                // the current index missing from it.
                shuffleIndices = emptyList()
            )
        }
        Timber.d("restoreDroppedFile: re-inserted ${removed.file.name} at $index")
    }

    /**
     * S1279: enforce the invariant that a source path a delete/move reported as succeeded is not
     * in the navigation list. [dropCurrentFile] normally got there first; this closes the case
     * where a failed operation was restored and then retried from the snackbar, which re-enqueues
     * without going through the optimistic path again.
     */
    fun removeFileByPath(path: String) {
        val currentState = stateFlow.value
        val index = currentState.files.indexOfFirst { it.path == path }
        if (index < 0) return

        val updatedFiles = currentState.files.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            currentState.currentIndex > index -> currentState.currentIndex - 1
            currentState.currentIndex >= updatedFiles.size -> 0
            else -> currentState.currentIndex
        }
        updateState {
            it.copy(
                files = updatedFiles,
                currentIndex = newIndex.coerceAtLeast(0),
                shuffleIndices = shuffleIndicesWithout(it.shuffleIndices, index)
            )
        }
        Timber.d("removeFileByPath: removed $index, ${currentState.files.size} -> ${updatedFiles.size}")
    }

    /**
     * Shuffle order holds positions into `files`, so removing one entry leaves every higher
     * position off by one and able to point past the end. Drop the removed one, close the gap.
     */
    private fun shuffleIndicesWithout(indices: List<Int>, removedIndex: Int): List<Int> =
        indices.asSequence()
            .filter { it != removedIndex }
            .map { if (it > removedIndex) it - 1 else it }
            .toList()

    /**
     * S1279: what [dropCurrentFile] took out, enough for [restoreDroppedFile] to put it back.
     * Kept here rather than handed to the caller so the whole API is path-keyed - the bookkeeping
     * belongs with the list it describes, and callers never have to name this type.
     */
    private data class RemovedFile(val file: MediaFile, val index: Int)

    fun nextFile(skipDocuments: Boolean = false, manual: Boolean = false) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Timber.d("╔═══════════════════════════════════════════════════════════════╗")
            Timber.d("║ PlayerViewModel.nextFile() CALLED                             ║")
            Timber.d("╚═══════════════════════════════════════════════════════════════╝")
            Timber.d("Caller: ${caller?.className}.${caller?.methodName}() at line ${caller?.lineNumber}")
            Timber.d("Thread: ${Thread.currentThread().name}")
            Timber.d("skipDocuments: $skipDocuments, manual: $manual")
        }

        val currentState = stateFlow.value
        if (currentState.files.isEmpty()) {
            Timber.d("nextFile: ABORT: No files to navigate")
            return
        }

        if (manual) clearPlaybackWatchdogs()

        var nextIndex = when (currentState.playbackOrderMode) {
            PlaybackOrderMode.LOOP_LIST -> {
                if (currentState.currentIndex >= currentState.files.size - 1) 0
                else currentState.currentIndex + 1
            }
            PlaybackOrderMode.PLAY_THROUGH -> {
                if (currentState.currentIndex >= currentState.files.size - 1) {
                    if (!manual) {
                        sendEvent(PlayerViewModel.PlayerEvent.StopPlayback)
                        return
                    }
                    0
                } else {
                    currentState.currentIndex + 1
                }
            }
            PlaybackOrderMode.SHUFFLE -> {
                val shuffleIndices = currentState.shuffleIndices
                val pos = shuffleIndices.indexOf(currentState.currentIndex)
                if (pos == -1 || pos >= shuffleIndices.size - 1) {
                    val files = currentState.files
                    val newIndices = files.indices.toMutableList().also { it.shuffle(kotlin.random.Random) }
                    val curIdx = currentState.currentIndex
                    val curPos = newIndices.indexOf(curIdx)
                    if (curPos == 0 && newIndices.size > 1) {
                        val swap = kotlin.random.Random.nextInt(1, newIndices.size)
                        newIndices[0] = newIndices[swap].also { newIndices[swap] = newIndices[0] }
                    } else if (curPos > 0) {
                        newIndices.removeAt(curPos)
                        newIndices.add(0, curIdx)
                    }
                    updateState { it.copy(shuffleIndices = newIndices) }
                    stateFlow.value.shuffleIndices[0]
                } else {
                    shuffleIndices[pos + 1]
                }
            }
            PlaybackOrderMode.REPEAT_ONE -> {
                updateState { it.copy(currentIndex = currentState.currentIndex) }
                saveResumeState()
                return
            }
        }

        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(nextIndex)
                val isDocument = file?.type == MediaType.TEXT ||
                    file?.type == MediaType.PDF ||
                    file?.type == MediaType.EPUB ||
                    file?.type == MediaType.OFFICE_DOCUMENT // S0301 Phase 05: Office files are documents too
                if (!isDocument) {
                    Timber.d("nextFile: Found media file at index $nextIndex")
                    break
                }
                Timber.d("nextFile: Skipping document at index $nextIndex")
                nextIndex = if (nextIndex >= currentState.files.size - 1) 0 else nextIndex + 1
                attempts++
            }
            if (attempts >= maxAttempts) {
                Timber.d("nextFile: All files are documents, staying on current")
                return
            }
        }

        Timber.d("nextFile: index ${currentState.currentIndex} → $nextIndex / ${currentState.files.size}")

        updateState { it.copy(currentIndex = nextIndex) }
        saveResumeState()

        if (currentState.resource != null && nextIndex < currentState.files.size) {
            saveLastViewedFileDebounced(currentState.files[nextIndex].path)
        }
    }

    fun previousFile(skipDocuments: Boolean = false, manual: Boolean = false) {
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = if (stackTrace.size > 3) stackTrace[3] else null
            Timber.d("╔═══════════════════════════════════════════════════════════════╗")
            Timber.d("║ PlayerViewModel.previousFile() CALLED                         ║")
            Timber.d("╚═══════════════════════════════════════════════════════════════╝")
            Timber.d("Caller: ${caller?.className}.${caller?.methodName}() at line ${caller?.lineNumber}")
            Timber.d("Thread: ${Thread.currentThread().name}")
            Timber.d("skipDocuments: $skipDocuments, manual: $manual")
        }

        val currentState = stateFlow.value
        if (currentState.files.isEmpty()) {
            Timber.d("previousFile: ABORT: No files to navigate")
            return
        }

        if (manual) clearPlaybackWatchdogs()

        var prevIndex = when (currentState.playbackOrderMode) {
            PlaybackOrderMode.LOOP_LIST, PlaybackOrderMode.PLAY_THROUGH -> {
                if (currentState.currentIndex <= 0) currentState.files.size - 1
                else currentState.currentIndex - 1
            }
            PlaybackOrderMode.SHUFFLE -> {
                val shuffleIndices = currentState.shuffleIndices
                val pos = shuffleIndices.indexOf(currentState.currentIndex)
                if (pos <= 0) shuffleIndices.lastOrNull() ?: (currentState.files.size - 1)
                else shuffleIndices[pos - 1]
            }
            PlaybackOrderMode.REPEAT_ONE -> {
                updateState { it.copy(currentIndex = currentState.currentIndex) }
                saveResumeState()
                return
            }
        }

        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(prevIndex)
                val isDocument = file?.type == MediaType.TEXT ||
                    file?.type == MediaType.PDF ||
                    file?.type == MediaType.EPUB ||
                    file?.type == MediaType.OFFICE_DOCUMENT // S0301 Phase 05: Office files are documents too
                if (!isDocument) {
                    Timber.d("previousFile: Found media file at index $prevIndex")
                    break
                }
                Timber.d("previousFile: Skipping document at index $prevIndex")
                prevIndex = if (prevIndex <= 0) currentState.files.size - 1 else prevIndex - 1
                attempts++
            }
            if (attempts >= maxAttempts) {
                Timber.d("previousFile: All files are documents, staying on current")
                return
            }
        }

        Timber.d("previousFile: index ${currentState.currentIndex} → $prevIndex / ${currentState.files.size}")

        updateState { it.copy(currentIndex = prevIndex) }
        saveResumeState()

        if (currentState.resource != null && prevIndex < currentState.files.size) {
            saveLastViewedFileDebounced(currentState.files[prevIndex].path)
        }
    }

    /**
     * Returns adjacent files for prefetch with assigned priorities.
     * Order: NEXT, PREV, then forward lookahead (+2, +3, ..).
     * Does not include current file.
     */
    fun getLookaheadTargets(maxLookahead: Int = 2): List<PlayerViewModel.LookaheadItem> {
        val currentState = stateFlow.value
        val files = currentState.files
        val currentIndex = currentState.currentIndex
        if (files.size <= 1) return emptyList()

        val result = mutableListOf<PlayerViewModel.LookaheadItem>()

        val nextIndex = (currentIndex + 1) % files.size
        result.add(
            PlayerViewModel.LookaheadItem(
                file = files[nextIndex],
                index = nextIndex,
                priority = RenderPriority.NEXT
            )
        )

        val prevIndex = if (currentIndex == 0) files.size - 1 else currentIndex - 1
        if (prevIndex != nextIndex) {
            result.add(
                PlayerViewModel.LookaheadItem(
                    file = files[prevIndex],
                    index = prevIndex,
                    priority = RenderPriority.PREVIOUS
                )
            )
        }

        for (offset in 2..maxLookahead + 1) {
            val lookaheadIndex = (currentIndex + offset) % files.size
            if (lookaheadIndex == currentIndex || lookaheadIndex == nextIndex || lookaheadIndex == prevIndex) continue
            result.add(
                PlayerViewModel.LookaheadItem(
                    file = files[lookaheadIndex],
                    index = lookaheadIndex,
                    priority = RenderPriority.LOOKAHEAD
                )
            )
        }

        return result
    }

    /** Adjacent images for Glide preloading (previous + next). Circular. */
    fun getAdjacentFiles(): List<MediaFile> {
        val currentState = stateFlow.value
        if (currentState.files.size <= 1) return emptyList()

        val result = mutableListOf<MediaFile>()

        val prevIndex = if (currentState.currentIndex <= 0) currentState.files.size - 1
                        else currentState.currentIndex - 1
        val prevFile = currentState.files.getOrNull(prevIndex)

        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) 0
                        else currentState.currentIndex + 1
        val nextFile = currentState.files.getOrNull(nextIndex)

        if (prevFile != null && (prevFile.type == MediaType.IMAGE || prevFile.type == MediaType.GIF)) {
            result.add(prevFile)
        }
        if (nextFile != null && nextFile != prevFile &&
            (nextFile.type == MediaType.IMAGE || nextFile.type == MediaType.GIF)) {
            result.add(nextFile)
        }
        return result
    }

    /**
     * Next audio file for network-source prefetch. Returns null when the next file is not
     * AUDIO - local playlists use ExoPlayer's own queue instead.
     */
    fun getNextAudioFile(): MediaFile? {
        val currentState = stateFlow.value
        if (currentState.files.size <= 1) return null

        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) 0
                        else currentState.currentIndex + 1
        val nextFile = currentState.files.getOrNull(nextIndex) ?: return null
        return if (nextFile.type == MediaType.AUDIO) nextFile else null
    }

    /** Persist `lastViewedFile` after a 5 s quiet period - reduces DB writes during slideshows. */
    private fun saveLastViewedFileDebounced(filePath: String) {
        val resource = stateFlow.value.resource ?: return
        saveLastViewedFileJob?.cancel()
        saveLastViewedFileJob = scope.launch {
            delay(5000)
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to save lastViewedFile")
            }
        }
    }

    /** Immediate write (used on activity pause). Cancels any pending debounced save. */
    fun saveLastViewedFile(filePath: String) {
        val resource = stateFlow.value.resource ?: return
        saveLastViewedFileJob?.cancel()
        scope.launch {
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                e.errorUnlessCancellation("Failed to save lastViewedFile")
            }
        }
    }
}
