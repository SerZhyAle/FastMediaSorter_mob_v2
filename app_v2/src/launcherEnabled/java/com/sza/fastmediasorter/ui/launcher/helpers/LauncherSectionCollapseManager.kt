package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.domain.repository.LauncherSectionVisibilityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * S1428: which launcher desktop sections are folded shut, and the tap that folds them (strategic §6.8).
 *
 * Owns nothing about the desktop's arrangement. Folding is applied when the desktop is drawn, so no
 * stored row or column is ever rewritten - which is the only reason expanding brings the arrangement
 * back rather than having to reconstruct it (strategic §5.1.6).
 *
 * A section is addressed by the encoded target of its header cell rather than by its row: a header the
 * user drags elsewhere is still the same section, and the row it left is not. S2317 pairs that target
 * with the header's desktop screen, because the starter set seeds one `SECTION_WIDGETS` header on
 * screen 0 and a second on screen 1 - the target alone folded both of them with one tap.
 *
 * Separate from [LauncherHomeViewModel][com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel]
 * because that class already sits at detekt's function ceiling, and because folded state is one
 * self-contained concern with its own persistence.
 */
class LauncherSectionCollapseManager(
    private val visibility: LauncherSectionVisibilityRepository,
    scope: CoroutineScope,
    private val cells: StateFlow<List<LauncherCellUi>>,
    private val orientation: StateFlow<LauncherOrientation>,
) {

    /** Bumped by [toggle]: the store is a plain preferences file and emits nothing on its own. */
    private val revision = MutableStateFlow(0)

    /** S2317: the identity of a section on one orientation - its desktop screen and its header target. */
    data class SectionKey(val screenIndex: Int, val target: String)

    /**
     * The folded sections right now, as the screen and encoded target of their header cells.
     *
     * Derived rather than held: which sections exist is already a function of the desktop, and which of
     * them are folded is a function of the orientation on top of that.
     *
     * Collected as a render trigger, never read to decide a fold: the render path asks
     * [collapsedTargetsFor] instead, which reads the store directly and so cannot see the empty set this
     * shared flow reports while nothing collects it.
     */
    val collapsed: StateFlow<Set<SectionKey>> =
        combine(cells, orientation, revision) { desktop, currentOrientation, _ ->
            desktop.map { it.cell }
                .filter { it.kind == LauncherCellKind.SECTION }
                .map { SectionKey(it.screenIndex, it.target) }
                .filter { visibility.isCollapsed(currentOrientation, it.screenIndex, it.target) }
                .toSet()
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptySet())

    /**
     * S2317: which of the headers in [drawn] are folded, as the plain targets the geometry layer takes.
     *
     * The projection from this class's screen-qualified truth onto one screen's cells. It exists because
     * [LauncherGridGeometry][com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry] and
     * [LauncherSectionMembership][com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership]
     * both run against the cells of a single screen, where a target is already unique - so handing them
     * a target set costs nothing and keeps that pure arithmetic free of a screen it cannot use.
     *
     * Takes the exact list being drawn rather than a screen index, so the single-screen render path -
     * which binds every cell regardless of its stored screen - is answered by the same function.
     *
     * Reads the repository rather than [collapsed], for the reason [revealIfCollapsed] records: that flow
     * is shared `WhileSubscribed` and reports an empty set whenever nothing is collecting it.
     */
    fun collapsedTargetsFor(drawn: List<LauncherCellUi>): Set<String> {
        val currentOrientation = orientation.value
        val folded = drawn.map { it.cell }
            .filter { it.kind == LauncherCellKind.SECTION }
            .filter { visibility.isCollapsed(currentOrientation, it.screenIndex, it.target) }
            .map { it.target }
            .toSet()
        return folded
    }

    /**
     * Folds [cell]'s section shut, or opens it again. The header's only gesture - strategic §6.8 ruled it
     * explicitly not long-pressable.
     */
    fun toggle(cell: LauncherCell) {
        if (cell.kind != LauncherCellKind.SECTION) return
        val currentOrientation = orientation.value
        val nowExpanded = visibility.isCollapsed(currentOrientation, cell.screenIndex, cell.target)
        visibility.setExpanded(currentOrientation, cell.screenIndex, cell.target, nowExpanded)
        revision.value += 1
    }

    /**
     * S2033: opens the section the cell with [cellId] landed in, once the desktop stream carries it.
     *
     * Lives here rather than in the ViewModel for two reasons. The desktop stream and the folded state are
     * both already this class's inputs, so nothing has to be passed back and forth to answer the question;
     * and the ViewModel sits exactly at detekt's function ceiling, which a reveal written there would push
     * over - the fix for that is to leave the function where its data is, not to widen the ceiling.
     *
     * Bounded rather than an open await: the desktop stream is the only thing that can report the row the
     * repository chose, and a wait on an emission that never arrives would outlive the gesture that asked
     * for it.
     */
    suspend fun revealSectionHolding(cellId: Long): Boolean {
        val desktop = withTimeoutOrNull(REVEAL_TIMEOUT_MS) {
            cells.first { drawn -> drawn.any { it.cell.id == cellId } }
        }?.map { it.cell }.orEmpty()
        val placed = desktop.firstOrNull { it.id == cellId }
        val owner = placed?.let { cell ->
            // S2317: membership is decided per screen. Ranking every screen's cells by row and column
            // would let a header on an earlier screen own a cell it is not even drawn beside.
            val sameScreen = desktop.filter { it.screenIndex == cell.screenIndex }
            LauncherSectionMembership.ownerOf(cell, LauncherSectionMembership.sectionsInOrder(sameScreen))
        }
        return owner?.let { revealIfCollapsed(it) } ?: false
    }

    /**
     * S2033: opens [cell]'s section when it is folded shut, and reports whether it opened one.
     *
     * Distinct from [toggle], which would close a section that is already open, and from [clear], whose
     * contract is "this header no longer exists" rather than "show what is inside this one". Conditional
     * because the owner ruled that a section is opened as a consequence of a cell being invisible, never
     * as a habit (strategic §3.1).
     *
     * Reads the repository rather than [collapsed]: that flow is shared `WhileSubscribed`, so it reports an
     * empty set whenever nothing is collecting it, and a placement that raced the last collector would
     * silently decide every section was already open.
     */
    private fun revealIfCollapsed(cell: LauncherCell): Boolean {
        val currentOrientation = orientation.value
        val hidden = cell.kind == LauncherCellKind.SECTION &&
            visibility.isCollapsed(currentOrientation, cell.screenIndex, cell.target)
        if (hidden) {
            visibility.reveal(currentOrientation, cell.screenIndex, cell.target)
            revision.value += 1
        }
        return hidden
    }

    /** A re-sorted section must be visible, so this exposes the manager-owned conditional reveal. */
    fun reveal(cell: LauncherCell): Boolean = revealIfCollapsed(cell)

    /**
     * S1742 §04.2: clears collapsed-state entry for [cell] when it is removed.
     *
     * Restores default (expanded) state, expressed as a reveal.
     */
    fun clear(cell: LauncherCell) {
        if (cell.kind != LauncherCellKind.SECTION) return
        visibility.reveal(orientation.value, cell.screenIndex, cell.target)
        revision.value += 1
    }

    private companion object {
        /** Matches the desktop stream this derives from, so both stop and restart together. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /**
         * S2033: how long a reveal waits for the desktop stream to carry the cell just written. Long
         * enough for a Room write to come back round the flow, short enough that a stream which never
         * emits does not leave the wait alive behind a finished gesture.
         */
        const val REVEAL_TIMEOUT_MS = 2_000L
    }
}
