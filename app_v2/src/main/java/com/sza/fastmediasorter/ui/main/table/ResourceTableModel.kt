package com.sza.fastmediasorter.ui.main.table

import com.sza.fastmediasorter.domain.model.AppSettings
import kotlin.math.max
import kotlin.math.roundToInt

/** The five sortable columns of the wide-window resource table, in on-screen order. */
enum class ResourceTableColumn { NAME, SOURCE, FILES, CONTENT, PATH }

/** A header sort: the column the rows are ordered by and its direction. */
data class ResourceTableSort(val column: ResourceTableColumn, val ascending: Boolean = true) {

    /** A repeated header press flips the direction; a press on another header starts it ascending. */
    fun pressed(header: ResourceTableColumn): ResourceTableSort =
        if (header == column) copy(ascending = !ascending) else ResourceTableSort(header)

    companion object {
        fun pressed(current: ResourceTableSort?, header: ResourceTableColumn): ResourceTableSort =
            current?.pressed(header) ?: ResourceTableSort(header)
    }
}

/**
 * The texts one row is searched and compared by. Built by the UI, because the source label and the
 * content summary are localized; [fileCount] is null when the count is unknown, which is not zero.
 */
data class ResourceTableKeys(
    val name: String,
    val source: String,
    val content: String,
    val path: String,
    val fileCount: Int?,
)

/**
 * S4041: search and header sort of the table. They order the VIEW only - the saved resource order is
 * never written from here, so a sorted table cannot reorganise the user's list.
 *
 * Pure by contract: no Android type enters this file.
 */
object ResourceTableRowsBuilder {

    fun <T> build(
        items: List<T>,
        query: String,
        sort: ResourceTableSort?,
        keysOf: (T) -> ResourceTableKeys,
    ): List<T> {
        val needle = query.trim()
        val keyed = items.map { it to keysOf(it) }
        val matched = if (needle.isEmpty()) {
            keyed
        } else {
            keyed.filter { (_, keys) ->
                keys.name.contains(needle, ignoreCase = true) || keys.path.contains(needle, ignoreCase = true)
            }
        }
        // sortedWith is stable, which is what keeps rows with equal keys in their saved order.
        val ordered = if (sort == null) matched else matched.sortedWith(comparatorFor<T>(sort))
        return ordered.map { it.first }
    }

    private fun <T> comparatorFor(sort: ResourceTableSort): Comparator<Pair<T, ResourceTableKeys>> =
        when (sort.column) {
            ResourceTableColumn.FILES -> fileCountComparator(sort.ascending)
            else -> {
                val text = Comparator<Pair<T, ResourceTableKeys>> { a, b ->
                    String.CASE_INSENSITIVE_ORDER.compare(
                        textOf(a.second, sort.column),
                        textOf(b.second, sort.column),
                    )
                }
                if (sort.ascending) text else text.reversed()
            }
        }

    /** An unknown count sorts after every known one in both directions, so a flip never hides it on top. */
    private fun <T> fileCountComparator(ascending: Boolean): Comparator<Pair<T, ResourceTableKeys>> =
        Comparator { a, b ->
            val left = a.second.fileCount
            val right = b.second.fileCount
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                ascending -> left.compareTo(right)
                else -> right.compareTo(left)
            }
        }

    private fun textOf(keys: ResourceTableKeys, column: ResourceTableColumn): String = when (column) {
        ResourceTableColumn.NAME -> keys.name
        ResourceTableColumn.SOURCE -> keys.source
        ResourceTableColumn.CONTENT -> keys.content
        ResourceTableColumn.PATH -> keys.path
        ResourceTableColumn.FILES -> ""
    }
}

/**
 * S4041 research 01: the table needs room for five readable columns plus the details panel. The
 * column part grows with the system text scale; the panel keeps its width and wraps its text instead.
 * Decided from the window width, so split screen and free-form windows fall back like a narrow phone.
 */
object ResourceTableWidthPolicy {

    const val TABLE_MIN_WIDTH_DP = 560
    const val DETAILS_PANEL_WIDTH_DP = 280

    fun minimumWindowWidthDp(fontScale: Float): Int =
        (TABLE_MIN_WIDTH_DP * max(1f, fontScale)).roundToInt() + DETAILS_PANEL_WIDTH_DP

    fun isTableEligible(windowWidthDp: Int, fontScale: Float): Boolean =
        windowWidthDp >= minimumWindowWidthDp(fontScale)
}

/** The view toggle applied to stored settings; [tableEligible] is the current window's answer. */
fun AppSettings.withNextResourceViewMode(tableEligible: Boolean): AppSettings {
    val next = ResourceViewMode.next(ResourceViewPreference(isResourceGridMode, isResourceTableMode), tableEligible)
    return copy(isResourceGridMode = next.grid, isResourceTableMode = next.table)
}

/** The stored view preferences: grid and table are kept apart so a narrow window cannot erase the table. */
data class ResourceViewPreference(val grid: Boolean, val table: Boolean)

/** What the resource area actually shows. */
enum class ResourceViewMode {
    LIST,
    GRID,
    TABLE;

    companion object {
        fun effective(preference: ResourceViewPreference, tableEligible: Boolean): ResourceViewMode = when {
            preference.table && tableEligible -> TABLE
            preference.grid -> GRID
            else -> LIST
        }

        /**
         * The view toggle's next preference. A wide window cycles list -> grid -> table -> list; a narrow
         * one toggles grid as before and leaves the table preference alone, so it is still there when
         * the window widens again.
         */
        fun next(preference: ResourceViewPreference, tableEligible: Boolean): ResourceViewPreference {
            if (!tableEligible) return preference.copy(grid = !preference.grid)
            return when (effective(preference, tableEligible = true)) {
                LIST -> ResourceViewPreference(grid = true, table = false)
                GRID -> ResourceViewPreference(grid = false, table = true)
                TABLE -> ResourceViewPreference(grid = false, table = false)
            }
        }
    }
}
