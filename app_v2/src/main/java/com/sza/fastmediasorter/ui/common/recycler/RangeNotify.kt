package com.sza.fastmediasorter.ui.common.recycler

import androidx.recyclerview.widget.ListAdapter

/**
 * Walks `0 until size` and invokes [onRun] once per maximal run of consecutive positions that
 * [isChanged] accepts, passing the run's first position and its length.
 *
 * S3282: one notifyItemChanged per item queues one RecyclerView UpdateOp per item, and
 * AdapterHelper.findPositionOffset / OpReorderer.getLastMoveOutOfOrder walk that pending list once
 * per op - over a large list the resulting main-thread stall was long enough for input dispatch to
 * time out. Collapsing contiguous positions into ranges bounds the queue by the number of runs.
 */
internal inline fun forEachChangedRun(
    size: Int,
    isChanged: (Int) -> Boolean,
    onRun: (start: Int, length: Int) -> Unit
) {
    var runStart = -1
    var runLength = 0
    for (index in 0 until size) {
        if (isChanged(index)) {
            if (runStart < 0) {
                runStart = index
            }
            runLength++
        } else if (runStart >= 0) {
            onRun(runStart, runLength)
            runStart = -1
            runLength = 0
        }
    }
    if (runStart >= 0) {
        onRun(runStart, runLength)
    }
}

/**
 * Notifies [payload] for every item [isChanged] accepts, collapsing contiguous positions into one
 * range notification. Use instead of a `forEachIndexed { .. notifyItemChanged(it) }` loop whenever a
 * selection or playback-state change can flip many rows at once - see [forEachChangedRun].
 */
fun <T> ListAdapter<T, *>.notifyChangedRuns(payload: Any, isChanged: (T) -> Boolean) {
    val list = currentList
    forEachChangedRun(
        size = list.size,
        isChanged = { index -> isChanged(list[index]) },
        onRun = { start, length -> notifyItemRangeChanged(start, length, payload) }
    )
}
