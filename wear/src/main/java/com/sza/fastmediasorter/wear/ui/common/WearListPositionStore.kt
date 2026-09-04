package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import javax.inject.Inject
import javax.inject.Singleton

/** One remembered list anchor: the item that sat in the middle of the screen, and its scroll offset. */
data class WearListPosition(val index: Int, val offset: Int)

/**
 * Remembers where each list was left, for the lifetime of the process (S2543).
 *
 * A watch screen leaves the composition the moment navigation pops it, and `SwipeDismissableNavHost`
 * destroys the whole `NavBackStackEntry` with it - so a list position kept in composition or in the
 * screen's ViewModel is gone by the time the user opens that screen again. This store outlives both.
 *
 * Deliberately not persisted: folder, stream and note lists change between app launches, and an index
 * restored into different content points at a different row. Within one session the content is the same.
 */
@Singleton
class WearListPositionStore @Inject constructor() {

    private val positions =
        object : LinkedHashMap<String, WearListPosition>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WearListPosition>): Boolean {
                return size > MAX_ENTRIES
            }
        }

    @Synchronized
    fun save(key: String, index: Int, offset: Int) {
        positions[key] = WearListPosition(index, offset)
    }

    @Synchronized
    fun peek(key: String): WearListPosition? = positions[key]

    @Synchronized
    fun clear(key: String) {
        positions.remove(key)
    }

    private companion object {
        const val MAX_ENTRIES = 32
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * The store as seen from composition. Null means no memory is available - every list then behaves
 * exactly as it did before this feature, which is what unit tests and previews get.
 */
val LocalWearListPositions = staticCompositionLocalOf<WearListPositionStore?> { null }
