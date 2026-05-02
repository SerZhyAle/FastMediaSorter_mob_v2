package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
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
    private val clearPlaybackWatchdogs: () -> Unit = {}
) {

    private var saveLastViewedFileJob: Job? = null

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

        var nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) {
            Timber.d("nextFile: Looping from last (${currentState.currentIndex}) to first (0)")
            0
        } else {
            Timber.d("nextFile: Moving from index ${currentState.currentIndex} to ${currentState.currentIndex + 1}")
            currentState.currentIndex + 1
        }

        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(nextIndex)
                val isDocument = file?.type == MediaType.TEXT ||
                    file?.type == MediaType.PDF ||
                    file?.type == MediaType.EPUB
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

        var prevIndex = if (currentState.currentIndex <= 0) {
            Timber.d("previousFile: Looping from first (${currentState.currentIndex}) to last (${currentState.files.size - 1})")
            currentState.files.size - 1
        } else {
            Timber.d("previousFile: Moving from index ${currentState.currentIndex} to ${currentState.currentIndex - 1}")
            currentState.currentIndex - 1
        }

        if (skipDocuments) {
            var attempts = 0
            val maxAttempts = currentState.files.size
            while (attempts < maxAttempts) {
                val file = currentState.files.getOrNull(prevIndex)
                val isDocument = file?.type == MediaType.TEXT ||
                    file?.type == MediaType.PDF ||
                    file?.type == MediaType.EPUB
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
     * AUDIO — local playlists use ExoPlayer's own queue instead.
     */
    fun getNextAudioFile(): MediaFile? {
        val currentState = stateFlow.value
        if (currentState.files.size <= 1) return null

        val nextIndex = if (currentState.currentIndex >= currentState.files.size - 1) 0
                        else currentState.currentIndex + 1
        val nextFile = currentState.files.getOrNull(nextIndex) ?: return null
        return if (nextFile.type == MediaType.AUDIO) nextFile else null
    }

    /** Persist `lastViewedFile` after a 5 s quiet period — reduces DB writes during slideshows. */
    private fun saveLastViewedFileDebounced(filePath: String) {
        val resource = stateFlow.value.resource ?: return
        saveLastViewedFileJob?.cancel()
        saveLastViewedFileJob = scope.launch {
            delay(5000)
            try {
                resourceRepository.updateResource(resource.copy(lastViewedFile = filePath))
                Timber.d("Saved lastViewedFile=$filePath for resource: ${resource.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lastViewedFile")
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
                Timber.e(e, "Failed to save lastViewedFile")
            }
        }
    }
}
