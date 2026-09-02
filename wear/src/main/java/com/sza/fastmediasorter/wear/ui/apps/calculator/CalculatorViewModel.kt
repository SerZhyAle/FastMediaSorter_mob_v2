package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorFunction
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistory
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Holds the calculator's session and writes its durable parts to the watch's own settings store.
 *
 * History and memory are persisted on every change rather than on leaving the screen: a watch program
 * is dismissed with a gesture that gives no callback worth relying on, so "save on exit" would lose
 * the session the first time the wrist dropped.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository
) : ViewModel() {

    private val engine = WearCalculatorEngine()
    private val history = WearCalculatorHistory()

    private var memory: BigDecimal? = null

    /**
     * S2152: the watch's display-mode setting, kept here so [publish] can hand it to the state
     * alongside everything the engine owns.
     */
    private var currentViewMode: WearViewMode = WearViewMode.LIST

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            history.restore(preferencesRepository.calculatorHistory.first())
            memory = preferencesRepository.calculatorMemory.first()?.toBigDecimalOrNull()
            publish()
        }
        // A collection rather than a first(): the setting can be changed while the calculator is open,
        // and the menu is expected to be laid out the way the watch is set at the moment it opens.
        viewModelScope.launch {
            preferencesRepository.viewMode.collect { mode ->
                currentViewMode = mode
                publish()
            }
        }
    }

    fun onDigit(digit: Int) = publishAfter { engine.inputDigit(digit) }

    fun onDecimal() = publishAfter { engine.inputDecimal() }

    fun onOperator(symbol: String) = publishAfter { engine.inputOperator(symbol) }

    fun onSign() = publishAfter { engine.toggleSign() }

    fun onBackspace() = publishAfter { engine.backspace() }

    fun onClear() = publishAfter { engine.clear() }

    fun onFunction(function: WearCalculatorFunction) = publishAfter { engine.apply(function) }

    fun onEquals() {
        val result = engine.inputEquals()
        val expression = engine.lastExpression
        if (expression != null) {
            history.add(WearCalculatorHistoryEntry(expression = expression, result = result))
            persistHistory()
        }
        publish()
    }

    fun onMemoryAdd() = changeMemory { current -> current.add(engine.currentValue()) }

    fun onMemorySubtract() = changeMemory { current -> current.subtract(engine.currentValue()) }

    fun onMemoryRecall() {
        val stored = memory ?: return
        engine.setResult(stored)
        publish()
    }

    fun onMemoryClear() {
        memory = null
        viewModelScope.launch { preferencesRepository.setCalculatorMemory(null) }
        publish()
    }

    /** Puts a stored result back on the display, so the history is a source of operands, not a log. */
    fun onHistoryEntryPicked(entry: WearCalculatorHistoryEntry) {
        engine.setResult(entry.result.toBigDecimalOrNull())
        publish()
    }

    fun onClearHistory() {
        history.clear()
        persistHistory()
        publish()
    }

    private fun changeMemory(transform: (BigDecimal) -> BigDecimal) {
        val next = transform(memory ?: BigDecimal.ZERO)
        memory = next
        viewModelScope.launch { preferencesRepository.setCalculatorMemory(next.toPlainString()) }
        publish()
    }

    private fun publishAfter(action: () -> String) {
        action()
        publish()
    }

    private fun persistHistory() {
        val serialized = history.serialize()
        viewModelScope.launch { preferencesRepository.setCalculatorHistory(serialized) }
    }

    private fun publish() {
        val display = engine.display()
        _uiState.value = CalculatorUiState(
            display = display,
            memoryOccupied = memory != null,
            history = history.entries(),
            isError = engine.isError,
            operation = shownOperation(),
            viewMode = currentViewMode,
            copyableValue = display.takeUnless { engine.isError }
        )
    }

    /**
     * S1942: the owner's chain of 2026-08-22 - the operation waiting to be applied, else the last one
     * chosen, else addition.
     *
     * It lives here and not in the composable because it is a product rule, and a rule worked out
     * while drawing would sit where nobody looks for rules.
     */
    private fun shownOperation(): WearCalculatorEngine.Operator =
        engine.pendingOperator ?: engine.lastChosenOperator ?: WearCalculatorEngine.Operator.PLUS
}
