package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import javax.inject.Inject

/** S0938: relative reorder of a pinned channel within the pinned set. */
enum class PinnedStreamMove { UP, DOWN, TO_TOP }

/**
 * S0938: move a pinned channel within the pinned set and persist the new order. Reads the pinned
 * snapshot, applies the move in memory, then hands the full id order to the repository, which
 * renumbers the set contiguously in one transaction. A no-op move (edge or missing id) writes nothing.
 */
class ReorderPinnedStreamUseCase @Inject constructor(
    private val repository: StreamSourceRepository
) {
    suspend operator fun invoke(id: String, move: PinnedStreamMove) {
        val pinned = repository.pinnedSnapshot()
        val from = pinned.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = when (move) {
            PinnedStreamMove.UP -> from - 1
            PinnedStreamMove.DOWN -> from + 1
            PinnedStreamMove.TO_TOP -> 0
        }.coerceIn(0, pinned.lastIndex)
        if (to == from) return
        val orderedIds = pinned.map { it.id }.toMutableList()
        orderedIds.add(to, orderedIds.removeAt(from))
        repository.reorderPinned(orderedIds)
    }
}
