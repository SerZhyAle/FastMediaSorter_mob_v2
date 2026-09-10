package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which groups of a grouped screen the user had opened, for the lifetime of the process
 * (S2806).
 *
 * A watch screen leaves the composition the moment navigation pops it, and a row scrolled off a
 * `ScalingLazyColumn` has its composition recycled - so a flag kept in either place is gone by the
 * time the user looks again. This store outlives both, exactly as [WearListPositionStore] does for
 * the scroll anchor beside it.
 *
 * A missing entry means COLLAPSED. That is what makes a report open as a short list of headings
 * without a second "never opened yet" state to carry: the first visit and a visit where everything
 * was closed are the same screen, so they may as well be the same record.
 *
 * Deliberately not persisted, for [WearListPositionStore]'s reason: a report's groups can differ
 * between app launches, and within one session they do not.
 */
@Singleton
class WearSectionExpansionStore @Inject constructor() {

    // A snapshot map rather than a plain one: the composition that reads it must recompose on toggle,
    // and the value is replaced wholesale so a set mutated in place cannot be missed.
    private val expanded = mutableStateMapOf<String, Set<Int>>()

    fun isExpanded(screenKey: String, id: Int): Boolean = expanded[screenKey]?.contains(id) == true

    fun toggle(screenKey: String, id: Int) {
        val current = expanded[screenKey].orEmpty()
        expanded[screenKey] = if (id in current) current - id else current + id
    }

    fun clear(screenKey: String) {
        expanded.remove(screenKey)
    }
}

/**
 * The store as seen from composition. Null means no memory is available - a screen then behaves
 * exactly as it did before this feature, which is what unit tests and previews get.
 */
val LocalWearSectionExpansion = staticCompositionLocalOf<WearSectionExpansionStore?> { null }
