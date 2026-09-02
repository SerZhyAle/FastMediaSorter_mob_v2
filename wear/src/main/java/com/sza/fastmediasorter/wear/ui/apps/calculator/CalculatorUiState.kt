package com.sza.fastmediasorter.wear.ui.apps.calculator

import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/** What the calculator screen draws. The arithmetic behind it lives in the domain engine. */
data class CalculatorUiState(
    val display: String = "0",
    val memoryOccupied: Boolean = false,
    val history: List<WearCalculatorHistoryEntry> = emptyList(),
    val isError: Boolean = false,
    /**
     * S1942: the operation the value row's element shows and repeats on a tap. Never null - the
     * view-model resolves it through the owner's fallback chain, so the element is never empty.
     */
    val operation: WearCalculatorEngine.Operator = WearCalculatorEngine.Operator.PLUS,
    /**
     * S2152: the watch's own display-mode setting, which the menu lays its rows out by. The initial
     * value matches every other screen in the module rather than the menu's old `GRID_3` guess - that
     * guess was the whole defect, because no caller ever replaced it.
     */
    val viewMode: WearViewMode = WearViewMode.LIST,
    /**
     * S2152: the value a tap would put on the clipboard, or null when there is nothing to copy.
     *
     * It is the same string [display] draws, so what lands in another app matches what the user was
     * looking at character for character. Null in the error state, where the row carries a word
     * rather than a number - the view model decides that, because which states are copyable is a
     * product rule and a rule worked out while drawing would sit where nobody looks for rules.
     */
    val copyableValue: String? = null
)
