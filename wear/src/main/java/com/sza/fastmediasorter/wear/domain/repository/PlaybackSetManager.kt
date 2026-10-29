package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * An ordered set of files together with the position inside it.
 *
 * @param files the list exactly as the browse screen showed it, after any sort or filter
 * @param index position of the open file; always a valid index of [files]
 */
data class PlaybackSet(
    val files: List<WearMediaFile>,
    val index: Int
) {
    val current: WearMediaFile? get() = files.getOrNull(index)
}

/**
 * Holds the file set the user is paging through: published by the browse screen, read by the
 * player screens.
 *
 * Navigation carries a file id and nothing else, so without this singleton a player cannot tell
 * which file follows the open one. Re-querying the repository is not an alternative: the browse
 * list may have been sorted or filtered, and a fresh query answers in a different order
 * (S1683 ADR-2).
 */
@Singleton
class PlaybackSetManager @Inject constructor() {

    private val _currentSet = MutableStateFlow<PlaybackSet?>(null)
    val currentSet: StateFlow<PlaybackSet?> = _currentSet.asStateFlow()

    /**
     * Publish the set the user is looking at.
     *
     * [files] is held by reference and never copied, so the caller must not mutate the list after
     * handing it over. A later publish replaces the previous set wholesale; sets are never merged.
     * An empty list is published as no set at all, so a consumer only ever sees a pageable set.
     */
    fun publish(files: List<WearMediaFile>, startIndex: Int) {
        if (files.isEmpty()) {
            Timber.w("PlaybackSetManager: empty set published, clearing instead")
            clear()
            return
        }
        val safeIndex = startIndex.coerceIn(0, files.lastIndex)
        Timber.d("PlaybackSetManager: published ${files.size} files at index $safeIndex")
        _currentSet.value = PlaybackSet(files, safeIndex)
    }

    /**
     * S1701: shuffle is a mode over this same set, not a second collection. Nothing is reordered or
     * stored - only the answer to "which file follows this one" changes, which is why turning it on
     * mid-playback costs nothing and cannot disagree with the list the browse screen published.
     */
    var shuffleEnabled: Boolean = false

    /**
     * S4471: the shuffle draw a peek made, so the file that was fetched ahead is the file [next] plays.
     * It is valid only for the list instance and the position it was drawn from; a new publish, a
     * removal or a move to another position leaves it unmatched and the next draw is fresh.
     */
    private var queuedShuffle: ShufflePick? = null

    /** Past the end the set wraps to its first file (strategic S1683 section 6.5). */
    fun next(): WearMediaFile? = moveByIndex { index, files ->
        successorIndex(index, files).also { queuedShuffle = null }
    }

    /**
     * S4471: the file [next] would answer, without moving to it. The audio player fetches it while the
     * current track plays, which is why shuffle has to commit to its draw here instead of at the call
     * to [next].
     */
    fun peekNext(): WearMediaFile? {
        val set = _currentSet.value
        return if (set == null || set.files.isEmpty()) {
            null
        } else {
            set.files[successorIndex(set.index, set.files)]
        }
    }

    /** Before the start the set wraps to its last file (strategic S1683 section 6.5). */
    fun previous(): WearMediaFile? = moveByIndex { index, files ->
        when {
            shuffleEnabled -> otherIndex(index, files.size)
            index > 0 -> index - 1
            else -> files.lastIndex
        }
    }

    /**
     * Point the set at [fileId]. False means there is no set, or the id is not part of it, which a
     * caller reads as "paging unavailable" rather than as an error.
     */
    fun moveTo(fileId: Long): Boolean {
        val set = _currentSet.value
        val target = set?.files?.indexOfFirst { it.id == fileId } ?: -1
        if (set == null || target < 0) {
            Timber.d("PlaybackSetManager: id=$fileId is not part of the published set")
            return false
        }
        _currentSet.value = set.copy(index = target)
        return true
    }

    /**
     * Drops a file that a successful operation changed and keeps the same browse ordering for the
     * player that must immediately continue. The index stays at the removed position, which selects
     * the following item, or the new final item when the removed item had been final.
     */
    fun removeAndSelectNext(fileId: Long): WearMediaFile? {
        val set = _currentSet.value
        val removedIndex = set?.files?.indexOfFirst { it.id == fileId } ?: -1
        return when {
            set == null || removedIndex < 0 -> null
            else -> selectAfterRemoval(set, fileId, removedIndex)
        }
    }

    private fun selectAfterRemoval(
        set: PlaybackSet,
        fileId: Long,
        removedIndex: Int
    ): WearMediaFile? {
        val remaining = set.files.filterNot { it.id == fileId }
        if (remaining.isEmpty()) {
            clear()
        } else {
            val nextIndex = removedIndex.coerceAtMost(remaining.lastIndex)
            _currentSet.value = PlaybackSet(remaining, nextIndex)
        }
        return _currentSet.value?.current
    }

    fun clear() {
        Timber.d("PlaybackSetManager: set cleared")
        _currentSet.value = null
    }

    private fun successorIndex(index: Int, files: List<WearMediaFile>): Int {
        if (!shuffleEnabled) {
            return (index + 1) % files.size
        }
        val queued = queuedShuffle?.takeIf { it.files === files && it.fromIndex == index }
        return queued?.toIndex ?: otherIndex(index, files.size).also {
            queuedShuffle = ShufflePick(files, index, it)
        }
    }

    private class ShufflePick(val files: List<WearMediaFile>, val fromIndex: Int, val toIndex: Int)

    /**
     * A uniform pick among every index except [current]. Drawing an offset in 1..size-1 rather than
     * an index in 0..size-1 removes the retry loop and with it the chance of repeating the file the
     * user is listening to right now, which reads as a broken shuffle rather than as chance.
     */
    private fun otherIndex(current: Int, size: Int): Int {
        if (size <= 1) {
            return current
        }
        return (current + Random.nextInt(size - 1) + 1) % size
    }

    private fun moveByIndex(
        nextIndex: (index: Int, files: List<WearMediaFile>) -> Int
    ): WearMediaFile? {
        val set = _currentSet.value
        if (set == null || set.files.isEmpty()) {
            return null
        }
        val moved = set.copy(index = nextIndex(set.index, set.files))
        _currentSet.value = moved
        return moved.current
    }
}
