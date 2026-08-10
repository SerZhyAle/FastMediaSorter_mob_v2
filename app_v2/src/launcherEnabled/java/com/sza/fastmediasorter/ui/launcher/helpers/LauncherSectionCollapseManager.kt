package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionStore
import com.sza.fastmediasorter.ui.common.widget.SharedPreferencesCollapsibleSectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * S1428: which launcher desktop sections are folded shut, and the tap that folds them (strategic §6.8).
 *
 * Owns nothing about the desktop's arrangement. Folding is applied when the desktop is drawn, so no
 * stored row or column is ever rewritten - which is the only reason expanding brings the arrangement
 * back rather than having to reconstruct it (strategic §5.1.6).
 *
 * A section is addressed by the encoded target of its header cell rather than by its row: a header the
 * user drags elsewhere is still the same section, and the row it left is not.
 *
 * Separate from [LauncherHomeViewModel][com.sza.fastmediasorter.ui.launcher.LauncherHomeViewModel]
 * because that class already sits at detekt's function ceiling, and because folded state is one
 * self-contained concern with its own persistence.
 */
class LauncherSectionCollapseManager(
    context: Context,
    scope: CoroutineScope,
    cells: StateFlow<List<LauncherCellUi>>,
    private val orientation: StateFlow<LauncherOrientation>,
) {

    // The consolidated namespace every other collapsible section in the app persists to, so this adds
    // no column and therefore no schema migration (strategic §3.2, data compatibility).
    private val store: CollapsibleSectionStore = SharedPreferencesCollapsibleSectionStore(context)

    /** Bumped by [toggle]: the store is a plain preferences file and emits nothing on its own. */
    private val revision = MutableStateFlow(0)

    /**
     * The folded sections right now, as the encoded targets of their header cells.
     *
     * Derived rather than held: which sections exist is already a function of the desktop, and which of
     * them are folded is a function of the orientation on top of that.
     */
    val collapsed: StateFlow<Set<String>> =
        combine(cells, orientation, revision) { desktop, currentOrientation, _ ->
            desktop.map { it.cell }
                .filter { it.kind == LauncherCellKind.SECTION }
                .map { it.target }
                .filterNot { store.isExpanded(keyFor(currentOrientation, it), EXPANDED_BY_DEFAULT) }
                .toSet()
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptySet())

    /**
     * Folds [cell]'s section shut, or opens it again. The header's only gesture - strategic §6.8 ruled it
     * explicitly not long-pressable.
     */
    fun toggle(cell: LauncherCell) {
        if (cell.kind != LauncherCellKind.SECTION) return
        val key = keyFor(orientation.value, cell.target)
        val nowExpanded = !store.isExpanded(key, EXPANDED_BY_DEFAULT)
        store.setExpanded(key, nowExpanded)
        revision.value += 1
        Timber.d("S1428: section '%s' expanded=%b key=%s", cell.target, nowExpanded, key)
    }

    /**
     * Portrait and landscape are two independent layouts (strategic §6.3), so the orientation is part of
     * the key: a section folded in one says nothing about the same section in the other.
     */
    private fun keyFor(orientation: LauncherOrientation, target: String): String =
        "$KEY_SCREEN${orientation.name}__$target"

    private companion object {
        /** `<screen>__<section>` is the store's own key shape; the orientation joins the screen half. */
        const val KEY_SCREEN = "launcher_desktop__"

        /** Strategic §6.8: a section is open until the user folds it, so "visible without a tap" holds. */
        const val EXPANDED_BY_DEFAULT = true

        /** Matches the desktop stream this derives from, so both stop and restart together. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
