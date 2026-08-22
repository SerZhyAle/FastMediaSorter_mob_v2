package com.sza.fastmediasorter.wear.ui.apps.calculator

import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistoryEntry

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
    val operation: WearCalculatorEngine.Operator = WearCalculatorEngine.Operator.PLUS
)
