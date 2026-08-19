package com.sza.fastmediasorter.wear.domain.calculator

const val DEFAULT_HISTORY_LIMIT = 50

private const val FIELD_SEPARATOR = '\u001F'
private const val FIELD_COUNT = 2

/** One finished calculation, kept whole so the history reads as expressions rather than results. */
data class WearCalculatorHistoryEntry(val expression: String, val result: String)

/**
 * The watch calculator's history, newest first and bounded.
 *
 * The phone stores its history in an append-only file with no cap. A watch cannot carry that: the
 * store is the same preferences file every other watch setting lives in, and an unbounded list there
 * grows for as long as the program is used and is read back in full on every start.
 */
class WearCalculatorHistory(private val maxEntries: Int = DEFAULT_HISTORY_LIMIT) {

    private val newestFirst = ArrayDeque<WearCalculatorHistoryEntry>()

    fun add(entry: WearCalculatorHistoryEntry) {
        newestFirst.addFirst(entry)
        while (newestFirst.size > maxEntries) {
            newestFirst.removeLast()
        }
    }

    fun entries(): List<WearCalculatorHistoryEntry> = newestFirst.toList()

    fun clear() {
        newestFirst.clear()
    }

    /** Newest first, so a restore preserves the order without the caller knowing the convention. */
    fun serialize(): List<String> = newestFirst.map { entry ->
        "${entry.expression}$FIELD_SEPARATOR${entry.result}"
    }

    /**
     * A line that does not carry both fields is dropped rather than reported: the only way to get one
     * is a store written by a different version, and refusing the whole history over it would cost
     * the user everything else in it.
     */
    fun restore(serialized: List<String>) {
        newestFirst.clear()
        serialized.asSequence()
            .map { it.split(FIELD_SEPARATOR) }
            .filter { it.size == FIELD_COUNT }
            .take(maxEntries)
            .forEach { fields -> newestFirst.addLast(WearCalculatorHistoryEntry(fields[0], fields[1])) }
    }
}
