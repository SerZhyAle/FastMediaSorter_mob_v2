package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.util.GridColumnFit

// S1965: was 26.dp - about half the interactive minimum, while the KDoc below and
// docs/WEAR_OS_STATUS.md both said 48. That KDoc names the target size as the FIXED side of the
// trade and scrolling as its price, so the constant was what stood out of line, not the rule.
private val KEY_HEIGHT = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val KEY_GAP = 2.dp
private val KEYPAD_SIDE_PADDING = 6.dp

/** S1942: the value row's operation element, held at the same interactive minimum as a keypad key. */
private val OPERATION_ELEMENT_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

/** S1719: how many zeros the held zero key enters, mirroring the phone's quick entry. */
private const val TRIPLE_ZERO_COUNT = 3

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
    // S1719: holding the menu key leaves the calculator. The screen does not own the back stack, so
    // leaving is the host's word, handed in - the same way every other route here stays navigation-free.
    onLeave: () -> Unit = {},
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
            CalculatorDisplay(
                uiState = uiState,
                // The same view-model entry the operator keys reach through `dispatch`; the element
                // repeats an operation, it does not open a second way into the arithmetic.
                onOperation = { symbol -> viewModel.onOperator(symbol) }
            )
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
                        onKey = { key -> dispatch(key, viewModel) { menuOpen = true } },
                        onLongKey = { key -> dispatchLongPress(key, viewModel, onLeave) },
                    )
                }
            }
        }

        if (menuOpen) {
            CalculatorMenuOverlay(
                memoryOccupied = uiState.memoryOccupied,
                viewModel = viewModel,
                onClose = { menuOpen = false },
                onHistory = {
                    menuOpen = false
                    historyOpen = true
                }
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

/**
 * The menu overlay and the wiring that closes it.
 *
 * Extracted from [CalculatorScreen] because every one of these actions ends the same way - do the
 * thing, then shut the menu - and eight repetitions of that pair inside the screen buried the screen's
 * own structure under its menu's bookkeeping.
 */
@Composable
private fun CalculatorMenuOverlay(
    memoryOccupied: Boolean,
    viewModel: CalculatorViewModel,
    onClose: () -> Unit,
    onHistory: () -> Unit,
) {
    CalculatorMenuSheet(
        memoryOccupied = memoryOccupied,
        actions = CalculatorMenuActions(
            onFunction = { function ->
                viewModel.onFunction(function)
                onClose()
            },
            onMemoryAdd = {
                viewModel.onMemoryAdd()
                onClose()
            },
            onMemorySubtract = {
                viewModel.onMemorySubtract()
                onClose()
            },
            onMemoryRecall = {
                viewModel.onMemoryRecall()
                onClose()
            },
            onMemoryClear = {
                viewModel.onMemoryClear()
                onClose()
            },
            onHistory = onHistory,
            onDismiss = onClose
        )
    )
}

@Composable
private fun CalculatorDisplay(uiState: CalculatorUiState, onOperation: (String) -> Unit) {
    val text = if (uiState.isError) stringResource(R.string.wear_calc_error) else uiState.display
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = KEYPAD_SIDE_PADDING, end = KEYPAD_SIDE_PADDING, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.title1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            // The number keeps its own type size and takes whatever the small element leaves;
            // strategic 3.2 fixes legibility, so the element is what stays small, not the value.
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = text }
        )
        OperationElement(operation = uiState.operation, onOperation = onOperation)
    }
}

/**
 * S1942: one element for both halves of the owner's ruling of 2026-08-22 - it shows the operation
 * that will be applied and repeats it on a tap, which is what let this ticket absorb S1900 instead of
 * putting a second control on the same row.
 *
 * A lone operator sign beside a number announces nothing to a screen reader, hence the spoken
 * description naming both the operation and what a tap does.
 */
@Composable
private fun OperationElement(operation: WearCalculatorEngine.Operator, onOperation: (String) -> Unit) {
    val description = stringResource(
        R.string.wear_calc_current_operation,
        stringResource(operationDescriptionRes(operation))
    )
    RectangularButton(
        onClick = {
            onOperation(operation.symbol)
        },
        modifier = Modifier
            .size(OPERATION_ELEMENT_SIZE)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.primaryButtonColors()
    ) {
        Text(
            text = operation.symbol,
            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

private fun operationDescriptionRes(operation: WearCalculatorEngine.Operator): Int = when (operation) {
    WearCalculatorEngine.Operator.PLUS -> R.string.wear_calc_plus
    WearCalculatorEngine.Operator.MINUS -> R.string.wear_calc_minus
    WearCalculatorEngine.Operator.TIMES -> R.string.wear_calc_times
    WearCalculatorEngine.Operator.DIVIDE -> R.string.wear_calc_divide
}

@Composable
private fun CalculatorKeyRow(
    keys: List<CalculatorKey>,
    onKey: (CalculatorKey) -> Unit,
    onLongKey: (CalculatorKey) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        keys.forEach { key ->
            val label = labelFor(key)
            val description = descriptionFor(key)
            RectangularButton(
                onClick = { onKey(key) },
                modifier = Modifier
                    .weight(1f)
                    .height(KEY_HEIGHT)
                    .semantics { contentDescription = description },
                onLongClick = longPressHandler(key, onLongKey),
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

/**
 * S1719: the long press for the two keys that carry one, and nothing for the rest.
 *
 * It is handed to the key itself rather than added to its modifier as a gesture detector. The key is
 * a [RectangularButton], which serves the tap and the long press from a single `combinedClickable`,
 * and a detector layered beside that handler is exactly the two-handlers-on-one-node arrangement
 * S1953 was opened for: one of the two gestures always loses the down, and the tap loses its ripple
 * and its `Role.Button` semantics along with it.
 */
private fun longPressHandler(
    key: CalculatorKey,
    onLongKey: (CalculatorKey) -> Unit,
): (() -> Unit)? {
    if (!hasLongPress(key)) return null
    return { onLongKey(key) }
}

/** S1719: the watch keypad stays arithmetic-only (owner, 2026-08-19) - exactly two keys carry more. */
private fun hasLongPress(key: CalculatorKey): Boolean =
    key == CalculatorKey.Menu || (key is CalculatorKey.Digit && key.value == 0)

/** S1719: three zeros through the ordinary digit path, and leaving from the menu key. */
private fun dispatchLongPress(
    key: CalculatorKey,
    viewModel: CalculatorViewModel,
    onLeave: () -> Unit,
) {
    when {
        key == CalculatorKey.Menu -> onLeave()
        key is CalculatorKey.Digit && key.value == 0 -> repeat(TRIPLE_ZERO_COUNT) { viewModel.onDigit(0) }
        else -> Unit
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

// S1966: the numbers here ARE the digits the keys carry, so naming each one as a constant would
// rename 7 to SEVEN and explain nothing. This is the case suppression exists for.
@Suppress("MagicNumber")
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
