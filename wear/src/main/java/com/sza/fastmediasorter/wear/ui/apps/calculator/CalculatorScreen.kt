package com.sza.fastmediasorter.wear.ui.apps.calculator

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit

// S2007, owner ruling 2026-08-26: half the interactive minimum, deliberately. S1965 had raised this
// to 48.dp because the KDoc and docs/WEAR_OS_STATUS.md both said 48 and the constant alone stood out
// of line - a rule inferred from two documents agreeing with each other. The owner then pressed the
// shipped keys on a real watch and priced the trade the other way: fitting all five rows and the
// value row on the glass at once is worth more than the 48 dp target, which no five-row keypad ever
// fitted anyway (S2007 section 6 item 3). The cost is a smaller key, stated rather than hidden.
// GridColumnFit.DEFAULT_MIN_TARGET_DP stays 48 dp for every other watch control.
private val KEY_HEIGHT = 24.dp
private val KEY_GAP = 2.dp
private val KEYPAD_SIDE_PADDING = 6.dp

/**
 * S2152: how many keypad columns the value itself spends, with one column left to each flank.
 *
 * Weights rather than a measured width, so the clear key and the operation element land exactly under
 * the outer keypad columns without this row repeating the keypad's column arithmetic.
 */
private const val VALUE_COLUMN_SPAN = 2f
private const val FLANK_COLUMN_SPAN = 1f

/**
 * S2152: how far a hugged label stays clear of the cell's own rounded corner.
 *
 * Pulling a label in from the bezel is worth nothing if it lands on the corner radius instead, so the
 * two edges the round glass crops get this much room between the glyph and the shape.
 */
private val LABEL_HUG_PADDING = 4.dp

/** S1719: how many zeros the held zero key enters, mirroring the phone's quick entry. */
private const val TRIPLE_ZERO_COUNT = 3

/** S2007: how much of the theme's error colour the two destructive keys carry as a plate. */
private const val DESTRUCTIVE_TINT_ALPHA = 0.35f

/**
 * S2007: empty space below the last row, so the bottom row can be scrolled to the middle of the
 * display instead of being parked against the rim.
 *
 * A round screen narrows towards its edges, and the scaling this keypad dropped was quietly paying
 * for that: a full-size row at the end of the viewport has its outer keys off the glass. Measured on
 * a 480 px round emulator at maximum scroll, the clear key's tap centre landed about 15 px outside a
 * circle of radius 240 - the key could not be pressed at all. The space survives the 2026-08-26
 * ruling that halved the key: a shorter row is likelier to fit at rest, which makes this cheap
 * insurance rather than dead weight, and it costs nothing until scrolled to. This is not the
 * `autoCentering` padding ADR-2
 * rejected: that sat ABOVE the first row and was why the keypad opened on emptiness, while this sits
 * below the last row and costs nothing until the user scrolls down to it.
 */
private val KEYPAD_TRAILING_SPACE = KEY_HEIGHT * 2

/**
 * What a key does when pressed. The screen holds no arithmetic - every action is one call into the
 * view model, which owns the engine.
 */
internal sealed interface CalculatorKey {
    data class Digit(val value: Int) : CalculatorKey
    data class Operator(val operator: WearCalculatorEngine.Operator) : CalculatorKey
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
 * 48 dp interactive minimum and a result line at the same time, so one side of that trade has to
 * give. Owner ruling 2026-08-26 made it the target size: [KEY_HEIGHT] is 24 dp, which is what puts
 * the rows on the glass. The scroll container stays, because nothing guarantees the fit on every
 * watch size and a keypad that cannot scroll would lose its last row on the smallest of them.
 *
 * S2007: what scrolls them is a plain [Column], not a `ScalingLazyColumn`. The scaling list spent
 * about half its own viewport on auto-centring padding that carried no key, and drew every row away
 * from the centre line smaller than the 48 dp the constant above promises - so the outer keys were
 * pressed at less than the minimum they claim to honour.
 */
@Composable
fun CalculatorScreen(
    // S1719: holding the menu key leaves the calculator. The screen does not own the back stack, so
    // leaving is the host's word, handed in - the same way every other route here stays navigation-free.
    onLeave: () -> Unit = {},
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keypadScrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var copyConfirmationShown by remember { mutableStateOf(false) }

    // Keyed on Unit so it marks entry into the screen, not every recomposition the keypad causes.
    LaunchedEffect(
        Unit
    ) { }

    // S2007: no `scrollState` is handed to the scaffold. That parameter exists only to scroll
    // `TimeText` away, and the value row is fixed below the clock while the keypad scrolls beneath
    // the value row - so nothing that moves here ever reaches the clock to obscure it.
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        positionIndicator = { PositionIndicator(keypadScrollState) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CalculatorDisplay(
                uiState = uiState,
                // S2152: the clear key moved into this row but not out of the keypad's dispatch, so
                // the view model keeps exactly the entry points it had.
                onKey = { key -> dispatch(key, viewModel) { menuOpen = true } },
                // The same view-model entry the operator keys reach through `dispatch`; the element
                // repeats an operation, it does not open a second way into the arithmetic.
                onOperation = { symbol -> viewModel.onOperator(symbol) },
                onCopy = { value ->
                    clipboard.setText(AnnotatedString(value))
                    // S2152 ADR-3: from API 33 the platform draws its own clipboard confirmation, and
                    // two confirmations of one action on a watch-sized screen read as a fault. Below
                    // it there is none at all, and this module supports back to 28.
                    copyConfirmationShown = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(keypadScrollState)
                    .padding(
                        start = KEYPAD_SIDE_PADDING,
                        top = KEY_GAP,
                        end = KEYPAD_SIDE_PADDING,
                        bottom = KEY_GAP + KEYPAD_TRAILING_SPACE
                    ),
                verticalArrangement = Arrangement.spacedBy(KEY_GAP)
            ) {
                keypadRows().forEach { row ->
                    CalculatorKeyRow(
                        cells = row,
                        onKey = { key -> dispatch(key, viewModel) { menuOpen = true } },
                        onLongKey = { key -> dispatchLongPress(key, viewModel, onLeave) },
                    )
                }
            }
        }

        if (menuOpen) {
            CalculatorMenuOverlay(
                memoryOccupied = uiState.memoryOccupied,
                viewMode = uiState.viewMode,
                viewModel = viewModel,
                onClose = { menuOpen = false },
                onHistory = {
                    menuOpen = false
                    historyOpen = true
                }
            )
        }

        if (copyConfirmationShown) {
            CopyConfirmation(onTimeout = { copyConfirmationShown = false })
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
    viewMode: WearViewMode,
    viewModel: CalculatorViewModel,
    onClose: () -> Unit,
    onHistory: () -> Unit,
) {
    CalculatorMenuSheet(
        memoryOccupied = memoryOccupied,
        viewMode = viewMode,
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

/**
 * S2152: the copy confirmation the watch draws for itself.
 *
 * It says only that the copy happened - the value is still on the display behind it, so repeating the
 * number here would spend the whole of a small screen restating what the user is already looking at.
 */
@Composable
private fun CopyConfirmation(onTimeout: () -> Unit) {
    Dialog(showDialog = true, onDismissRequest = onTimeout) {
        Confirmation(onTimeout = onTimeout) {
            Text(
                text = stringResource(R.string.wear_calc_copied),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalculatorDisplay(
    uiState: CalculatorUiState,
    onKey: (CalculatorKey) -> Unit,
    onOperation: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    val text = if (uiState.isError) stringResource(R.string.wear_calc_error) else uiState.display
    val copyableValue = uiState.copyableValue
    // The spoken description names the tap only where the tap does something, so the error state is
    // not announced as offering an action it refuses to perform.
    val valueDescription = if (copyableValue != null) {
        stringResource(R.string.wear_calc_value_copyable, text)
    } else {
        text
    }
    val insets = wearScreenInsets()
    val layoutDirection = LocalLayoutDirection.current
    val startInset = insets.calculateStartPadding(layoutDirection)
    val endInset = insets.calculateEndPadding(layoutDirection)
    // Validate target column count via column-fit helper: GridColumnFit.columnsFor(WearViewMode.GRID_3, 192)
    val targetColumns = GridColumnFit.columnsFor(WearViewMode.GRID_3, 192)

    Row(
        // S2152: this bottom padding and the keypad column's `top = KEY_GAP` are together the gap the
        // owner asked about between the clear key and the seven key - it already existed as the gap
        // between the value row and the first keypad row, and clear inherits it by moving up here.
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = endInset, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClearKey(
            modifier = Modifier.weight(FLANK_COLUMN_SPAN),
            onClick = { onKey(CalculatorKey.Clear) }
        )
        Text(
            text = text,
            style = MaterialTheme.typography.title1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // S2007: right-aligned and in its own colour, which is where a calculator register puts
            // its value and what stops it reading as one more key label. The error word keeps the
            // error colour - an `Error` string drawn in the value colour claims to be a value.
            textAlign = TextAlign.End,
            color = if (uiState.isError) {
                MaterialTheme.colors.error
            } else {
                colorResource(R.color.wear_calc_display_value)
            },
            // The number keeps its own type size and takes whatever the small element leaves;
            // strategic 3.2 fixes legibility, so the element is what stays small, not the value.
            modifier = Modifier
                .weight(VALUE_COLUMN_SPAN)
                // S2152: the gesture is on the value alone and not on the row, so it never shares a
                // touch target with the operation element standing beside it.
                .then(
                    if (copyableValue != null) {
                        Modifier.clickable { onCopy(copyableValue) }
                    } else {
                        Modifier
                    }
                )
                .semantics { contentDescription = valueDescription }
        )
        OperationElement(
            operation = uiState.operation,
            onOperation = onOperation,
            modifier = Modifier.weight(FLANK_COLUMN_SPAN)
        )
    }
}

/**
 * S2152: clear stands in the value row rather than in the scrolling keypad.
 *
 * S2007 criterion 7 measured this key's tap centre about 15 px outside a 240 px radius at maximum
 * scroll. The value row does not scroll at all, so the position that measurement was taken in no
 * longer exists for this key - the failure mode is removed rather than made less likely. It keeps the
 * destructive plate [keyColorsFor] gives it, because moving a key must not quietly restate what its
 * colour means (S2007 ADR-4).
 */
@Composable
private fun ClearKey(modifier: Modifier, onClick: () -> Unit) {
    val description = descriptionFor(CalculatorKey.Clear)
    RectangularButton(
        onClick = onClick,
        modifier = modifier
            .height(KEY_HEIGHT)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp),
        colors = keyColorsFor(CalculatorKey.Clear)
    ) {
        Text(
            text = labelFor(CalculatorKey.Clear),
            style = labelStyleFor(CalculatorKey.Clear),
            maxLines = 1,
            // First position of its row, so it hugs the end edge - the same column rule the keypad's
            // own first column follows.
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = LABEL_HUG_PADDING)
        )
    }
}

/**
 * S1942: one element for both halves of the owner's ruling of 2026-08-22 - it shows the operation
 * that will be applied and repeats it on a tap, which is what let this ticket absorb S1900 instead of
 * putting a second control on the same row.
 *
 * A lone operator sign beside a number announces nothing to a screen reader, hence the spoken
 * description naming both the operation and what a tap does.
 *
 * S2152: it is sized like a keypad key - one column wide, [KEY_HEIGHT] tall - and no longer at the
 * 48 dp interactive minimum S1942 gave it. That size was set before the owner ruling of 2026-08-26
 * halved the key, so the element was out of step with the keypad rather than deliberately larger than
 * it, and on the glass it read as the loudest thing in the row. Both halves of the S1942 ruling
 * survive the change: it still shows the operation and still repeats it on a tap.
 */
@Composable
private fun OperationElement(
    operation: WearCalculatorEngine.Operator,
    onOperation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(
        R.string.wear_calc_current_operation,
        stringResource(operationDescriptionRes(operation))
    )
    RectangularButton(
        onClick = {
            onOperation(operation.symbol)
        },
        modifier = modifier
            .height(KEY_HEIGHT)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.primaryButtonColors()
    ) {
        Text(
            text = glyphFor(operation),
            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            // S2152: this element stands in the last column of the value row, so it takes the same
            // column rule as a last-column key rather than being treated as its own special case.
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = LABEL_HUG_PADDING)
        )
    }
}

/**
 * S2007: what an operator is drawn as, which is deliberately not what the engine parses.
 *
 * `MINUS` paints U+2212 MINUS SIGN because an ASCII hyphen is drawn at a fraction of the width of
 * `+`, `×` and `÷` and reads as a stray mark beside them. It stays out of
 * [WearCalculatorEngine.Operator.symbol], which `Operator.from` parses back and which is written
 * into the persisted history expressions the user can tap to reuse (ADR-3): a change made for how a
 * key looks must not travel into what a stored expression contains.
 */
private fun glyphFor(operator: WearCalculatorEngine.Operator): String = when (operator) {
    WearCalculatorEngine.Operator.PLUS -> "+"
    WearCalculatorEngine.Operator.MINUS -> "−"
    WearCalculatorEngine.Operator.TIMES -> "×"
    WearCalculatorEngine.Operator.DIVIDE -> "÷"
}

private fun operationDescriptionRes(operation: WearCalculatorEngine.Operator): Int = when (operation) {
    WearCalculatorEngine.Operator.PLUS -> R.string.wear_calc_plus
    WearCalculatorEngine.Operator.MINUS -> R.string.wear_calc_minus
    WearCalculatorEngine.Operator.TIMES -> R.string.wear_calc_times
    WearCalculatorEngine.Operator.DIVIDE -> R.string.wear_calc_divide
}

@Composable
private fun CalculatorKeyRow(
    cells: List<CalculatorCell>,
    onKey: (CalculatorKey) -> Unit,
    onLongKey: (CalculatorKey) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
    ) {
        cells.forEach { cell ->
            val key = cell.key
            val label = labelFor(key)
            val description = descriptionFor(key)
            RectangularButton(
                onClick = { onKey(key) },
                modifier = Modifier
                    // S2152: the span is what makes a key wider than a column expressible at all; a
                    // span of one reproduces the equal-cell row this keypad had before.
                    .weight(cell.span.toFloat())
                    .height(KEY_HEIGHT)
                    .semantics { contentDescription = description },
                onLongClick = longPressHandler(key, onLongKey),
                shape = RoundedCornerShape(4.dp),
                colors = keyColorsFor(key)
            ) {
                Text(
                    text = label,
                    style = labelStyleFor(key),
                    maxLines = 1,
                    // S2152: only the drawing of the label moves. The tap area, the height and the
                    // order of the keys are the ones the owner already pressed on the watch.
                    modifier = Modifier
                        .align(cell.labelAlignment)
                        .padding(horizontal = LABEL_HUG_PADDING)
                )
            }
        }
    }
}

/**
 * S2007: one type step larger for the low-ink glyphs.
 *
 * Equal type size gives unequal drawn size - a decimal point covers a fraction of the box a digit
 * fills - so the step is what makes the ink each key puts on the display comparable, rather than
 * the type size being equal while the keys look nothing alike.
 */
@Composable
private fun labelStyleFor(key: CalculatorKey): TextStyle {
    val base = when (key) {
        is CalculatorKey.Operator,
        CalculatorKey.Decimal,
        CalculatorKey.Sign,
        CalculatorKey.Equals,
        CalculatorKey.Menu -> MaterialTheme.typography.title1

        is CalculatorKey.Digit,
        CalculatorKey.Clear,
        CalculatorKey.Backspace -> MaterialTheme.typography.title2
    }
    return base.copy(fontWeight = FontWeight.Bold)
}

/**
 * S2007: the two destructive keys draw on a red plate so neither is mistaken for the arithmetic
 * beside it.
 *
 * The tint derives from the theme rather than a literal, so if S2003 later replaces the watch
 * palette these keys follow it instead of standing out of line. The content colour is
 * `onBackground` and not `onError` (ADR-4): Wear's `onError` is a dark colour meant for a fully
 * saturated error surface, and over a translucent tint on a black background it would be close to
 * unreadable.
 */
@Composable
private fun keyColorsFor(key: CalculatorKey): ButtonColors = when (key) {
    CalculatorKey.Clear,
    CalculatorKey.Backspace -> ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.error.copy(alpha = DESTRUCTIVE_TINT_ALPHA),
        contentColor = MaterialTheme.colors.onBackground
    )

    is CalculatorKey.Digit -> ButtonDefaults.secondaryButtonColors()

    is CalculatorKey.Operator,
    CalculatorKey.Decimal,
    CalculatorKey.Sign,
    CalculatorKey.Equals,
    CalculatorKey.Menu -> ButtonDefaults.primaryButtonColors()
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
        is CalculatorKey.Operator -> viewModel.onOperator(key.operator.symbol)
        CalculatorKey.Decimal -> viewModel.onDecimal()
        CalculatorKey.Sign -> viewModel.onSign()
        CalculatorKey.Equals -> viewModel.onEquals()
        CalculatorKey.Clear -> viewModel.onClear()
        CalculatorKey.Backspace -> viewModel.onBackspace()
        CalculatorKey.Menu -> openMenu()
    }
}

private fun labelFor(key: CalculatorKey): String = when (key) {
    is CalculatorKey.Digit -> key.value.toString()
    is CalculatorKey.Operator -> glyphFor(key.operator)
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
    is CalculatorKey.Operator -> stringResource(operationDescriptionRes(key.operator))
    CalculatorKey.Decimal -> stringResource(R.string.wear_calc_decimal)
    CalculatorKey.Sign -> stringResource(R.string.wear_calc_sign)
    CalculatorKey.Equals -> stringResource(R.string.wear_calc_equals)
    CalculatorKey.Clear -> stringResource(R.string.wear_calc_clear)
    CalculatorKey.Backspace -> stringResource(R.string.wear_calc_backspace)
    CalculatorKey.Menu -> stringResource(R.string.wear_calc_menu)
}
