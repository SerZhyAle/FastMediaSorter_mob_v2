package com.sza.fastmediasorter.wear.ui.apps.calculator

import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistoryEntry

/** What the calculator screen draws. The arithmetic behind it lives in the domain engine. */
data class CalculatorUiState(
    val display: String = "0",
    val memoryOccupied: Boolean = false,
    val history: List<WearCalculatorHistoryEntry> = emptyList(),
    val isError: Boolean = false
)
