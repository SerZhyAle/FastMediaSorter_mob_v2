package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold

private val KEY_HEIGHT = 26.dp
private val KEY_GAP = 2.dp
private val KEYPAD_SIDE_PADDING = 6.dp
private val DISPLAY_VERTICAL_PADDING = 8.dp

/**
 * What a key does when pressed. The screen holds no arithmetic - every action is one call into the
 * view model, which owns the engine.
 */
private sealed interface CalculatorKey {
    data class Digit(val value: Int) : CalculatorKey
    data class Operator(val symbol: String, val descriptionRes: Int) : CalculatorKey
    data object Decimal : CalculatorKey
    data object Sign : CalculatorKey
    data object Equals : CalculatorKey
    data object Clear : CalculatorKey
    data object Backspace : CalculatorKey
    data object Menu : CalculatorKey
}

/**
 * The keypad carries counting only; every function and the history sit behind the single menu key
 * (owner ruling 2026-08-19).
 *
 * The keypad scrolls rather than filling the screen. A watch display cannot hold twenty keys at the
 * 48 dp interactive minimum and a result line at the same time, and the strategic constraint makes
 * the target size the fixed side of that trade, not the key set - so the rows scroll instead of the
 * keys shrinking.
 */
@Composable
fun CalculatorScreen(
    navController: NavController,
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CalculatorDisplay(uiState = uiState)
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = KEYPAD_SIDE_PADDING, vertical = KEY_GAP)
            ) {
                items(keypadRows()) { row ->
                    CalculatorKeyRow(
                        keys = row,
                        onKey = { key -> dispatch(key, viewModel) { menuOpen = true } }
                    )
                }
            }
        }

        if (menuOpen) {
            CalculatorMenuSheet(
                memoryOccupied = uiState.memoryOccupied,
                onFunction = { function ->
                    viewModel.onFunction(function)
                    menuOpen = false
                },
                onMemoryAdd = { viewModel.onMemoryAdd(); menuOpen = false },
                onMemorySubtract = { viewModel.onMemorySubtract(); menuOpen = false },
                onMemoryRecall = { viewModel.onMemoryRecall(); menuOpen = false },
                onMemoryClear = { viewModel.onMemoryClear(); menuOpen = false },
                onHistory = { menuOpen = false; historyOpen = true },
                onDismiss = { menuOpen = false }
            )
        }

        if (historyOpen) {
            CalculatorHistoryPage(
                entries = uiState.history,
                onEntryPicked = { entry ->
                    viewModel.onHistoryEntryPicked(entry)
                    historyOpen = false
                },
                onClearHistory = viewModel::onClearHistory,
                onDismiss = { historyOpen = false }
            )
        }
    }
}

@Composable
private fun CalculatorDisplay(uiState: CalculatorUiState) {
    val text = if (uiState.isError) stringResource(R.string.wear_calc_error) else uiState.display
    Text(
        text = text,
        style = MaterialTheme.typography.title1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = KEYPAD_SIDE_PADDING, end = KEYPAD_SIDE_PADDING, top = 16.dp, bottom = 4.dp)
            .semantics { contentDescription = text }
    )
}

@Composable
private fun CalculatorKeyRow(
    keys: List<CalculatorKey>,
    onKey: (CalculatorKey) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        keys.forEach { key ->
            val label = labelFor(key)
            val description = descriptionFor(key)
            Button(
                onClick = { onKey(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(KEY_HEIGHT)
                    .semantics { contentDescription = description },
                shape = RoundedCornerShape(4.dp),
                colors = if (key is CalculatorKey.Digit) {
                    ButtonDefaults.secondaryButtonColors()
                } else {
                    ButtonDefaults.primaryButtonColors()
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

private fun dispatch(key: CalculatorKey, viewModel: CalculatorViewModel, openMenu: () -> Unit) {
    when (key) {
        is CalculatorKey.Digit -> viewModel.onDigit(key.value)
        is CalculatorKey.Operator -> viewModel.onOperator(key.symbol)
        CalculatorKey.Decimal -> viewModel.onDecimal()
        CalculatorKey.Sign -> viewModel.onSign()
        CalculatorKey.Equals -> viewModel.onEquals()
        CalculatorKey.Clear -> viewModel.onClear()
        CalculatorKey.Backspace -> viewModel.onBackspace()
        CalculatorKey.Menu -> openMenu()
    }
}

private fun keypadRows(): List<List<CalculatorKey>> = listOf(
    listOf(
        CalculatorKey.Digit(7),
        CalculatorKey.Digit(8),
        CalculatorKey.Digit(9),
        CalculatorKey.Operator("÷", R.string.wear_calc_divide)
    ),
    listOf(
        CalculatorKey.Digit(4),
        CalculatorKey.Digit(5),
        CalculatorKey.Digit(6),
        CalculatorKey.Operator("×", R.string.wear_calc_times)
    ),
    listOf(
        CalculatorKey.Digit(1),
        CalculatorKey.Digit(2),
        CalculatorKey.Digit(3),
        CalculatorKey.Operator("-", R.string.wear_calc_minus)
    ),
    listOf(
        CalculatorKey.Decimal,
        CalculatorKey.Digit(0),
        CalculatorKey.Sign,
        CalculatorKey.Operator("+", R.string.wear_calc_plus)
    ),
    listOf(
        CalculatorKey.Clear,
        CalculatorKey.Backspace,
        CalculatorKey.Menu,
        CalculatorKey.Equals
    )
)

private fun labelFor(key: CalculatorKey): String = when (key) {
    is CalculatorKey.Digit -> key.value.toString()
    is CalculatorKey.Operator -> key.symbol
    CalculatorKey.Decimal -> "."
    CalculatorKey.Sign -> "±"
    CalculatorKey.Equals -> "="
    CalculatorKey.Clear -> "C"
    CalculatorKey.Backspace -> "⌫"
    CalculatorKey.Menu -> "⋯"
}

@Composable
private fun descriptionFor(key: CalculatorKey): String = when (key) {
    is CalculatorKey.Digit -> key.value.toString()
    is CalculatorKey.Operator -> stringResource(key.descriptionRes)
    CalculatorKey.Decimal -> stringResource(R.string.wear_calc_decimal)
    CalculatorKey.Sign -> stringResource(R.string.wear_calc_sign)
    CalculatorKey.Equals -> stringResource(R.string.wear_calc_equals)
    CalculatorKey.Clear -> stringResource(R.string.wear_calc_clear)
    CalculatorKey.Backspace -> stringResource(R.string.wear_calc_backspace)
    CalculatorKey.Menu -> stringResource(R.string.wear_calc_menu)
}
