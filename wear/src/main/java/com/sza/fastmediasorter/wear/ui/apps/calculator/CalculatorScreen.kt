package com.sza.fastmediasorter.wear.ui.apps.calculator

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorEngine
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordance
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceRole
import com.sza.fastmediasorter.wear.ui.common.WearFitText
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit

// S2007, owner ruling 2026-08-26: half the interactive minimum, deliberately. S1965 had raised this
// to 48.dp because the KDoc and docs/WEAR_OS_STATUS.md both said 48 and the constant alone stood out
// of line - a rule inferred from two documents agreeing with each other. The owner then pressed the
// shipped keys on a real watch and priced the trade the other way: fitting all five rows and the
// value row on the glass at once is worth more than the 48 dp target, which no five-row keypad ever
// fitted anyway (S2007 section 6 item 3). The cost is a smaller key, stated rather than hidden.
// GridColumnFit.DEFAULT_MIN_TARGET_DP stays 48 dp for every other watch control.
internal val KEY_HEIGHT = 25.dp
internal val KEY_GAP = 2.dp

/** A low-contrast alternate plate makes the odd digits form a readable checkerboard. */
private const val ODD_DIGIT_TINT_ALPHA = 0.12f

private const val EVEN_DIVISOR = 2

/**
 * S2273: the size the system PUBLISHES for a node, which is not the size it was laid out at.
 *
 * A node smaller than the platform's minimum touch target is reported to accessibility - and so to
 * every shape check, `adb.ps1 clip-check` and Play's reviewer included - inflated to this size about
 * its own centre, then trimmed only where a neighbour's inflated box meets it. Measured on
 * `Wear_OS_Small_Round` 2026-09-08: the operation element was laid out 33.1 dp wide inside a row that
 * ended 15.6 px further left, and was published at exactly 48 dp, its right edge 211.4 px from the
 * centre of a 192 px glass. The row's own arithmetic was right and the published box was still off the
 * glass, which is why the 2026-09-04 fix passed its log probe and was rejected again.
 *
 * Both children of the value row therefore carry this size in the dimension where they fall short of
 * it: the row is at least this tall so the inflation has nowhere to grow upwards, and the operation
 * element is exactly this wide so it has nowhere to grow sideways. What is judged is then the row's
 * real box, which each flavor's `CalculatorBody` keeps whole.
 *
 * S3104 aligns those children to the bottom of the row in noLegal, which leaves this argument intact: a child
 * shorter than the target inflates about its own centre, and a child resting on the row's bottom edge
 * inflates upwards into the row's own free height rather than past its top edge.
 */
internal val TOUCH_TARGET = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

/** S2493: how many columns a keypad row divides into, which is what sizes the clear key below it. */
private const val KEYPAD_COLUMNS = 4

/**
 * S2493: the clear key is two columns wide - the same span `=` carries, which is this screen's own
 * word for a large target rather than a second, invented size.
 */
private const val CLEAR_COLUMN_SPAN = 2

/** The reset target is deliberately twice as tall as an ordinary keypad key. */
private const val CLEAR_KEY_HEIGHT_MULTIPLIER = 2

/**
 * S2493: the empty space between the last keypad row and the clear key, as a fraction of one normal
 * keypad key's height (owner ruling 2026-09-03).
 *
 * The gap stays tied to the regular key rather than to the wider reset control, so it remains modest
 * when the screen size changes. `=` occupies the two columns directly above the half of this row left
 * empty, so an overshot `=` still has to cross the gap to reach reset.
 */
private const val CLEAR_ROW_GAP_FRACTION = 1f / 3f

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
 * give. Owner ruling 2026-08-26 made it the target size: [KEY_HEIGHT] is 25 dp, which is what puts
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
    // S2754: the two overlays scroll their own lists inside this same Scaffold, so their states are
    // held here - an indicator left on the keypad marks a column nothing is touching while one is up.
    val menuListState = rememberWearListState()
    val historyListState = rememberWearListState()
    var copyConfirmationShown by remember { mutableStateOf(false) }
    // S2007: no `scrollState` is handed to the scaffold. That parameter exists only to scroll
    // `TimeText` away, and the value row is fixed below the clock while the keypad scrolls beneath
    // the value row - so nothing that moves here ever reaches the clock to obscure it.
    // The one screen that opts out of the app wallpaper (owner ruling 2026-09-04): the calculator is
    // worked on, not looked at, and a moving or photographic backdrop pulls the eye off the digits.
    // An opaque black container also keeps the keypad's contrast independent of the chosen picture.
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        positionIndicator = {
            when {
                historyOpen -> PositionIndicator(historyListState)
                menuOpen -> PositionIndicator(menuListState)
                else -> PositionIndicator(keypadScrollState)
            }
        },
        background = Color.Black
    ) {
        // S3192: where the value row and the keypad stand is the flavor's decision (owner ruling
        // 2026-09-16) - noLegal keeps the S3104 layout, standard keeps every node inside the glass.
        CalculatorBody(
            uiState = uiState,
            keypadScrollState = keypadScrollState,
            // The same view-model entry the operator keys reach through `dispatch`; the element
            // repeats an operation, it does not open a second way into the arithmetic.
            onOperation = { symbol -> viewModel.onOperator(symbol) },
            onCopy = { value ->
                clipboard.setText(AnnotatedString(value))
                // S2152 ADR-3: from API 33 the platform draws its own clipboard confirmation, and
                // two confirmations of one action on a watch-sized screen read as a fault. Below
                // it there is none at all, and this module supports back to 28.
                copyConfirmationShown = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            },
            onKey = { key -> dispatch(key, viewModel) { menuOpen = true } },
            onLongKey = { key -> dispatchLongPress(key, viewModel, onLeave) },
            onLeave = onLeave
        )

        if (menuOpen) {
            CalculatorMenuOverlay(
                memoryOccupied = uiState.memoryOccupied,
                viewMode = uiState.viewMode,
                listState = menuListState,
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
                listState = historyListState,
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
    listState: ScalingLazyListState,
    viewModel: CalculatorViewModel,
    onClose: () -> Unit,
    onHistory: () -> Unit,
) {
    CalculatorMenuSheet(
        memoryOccupied = memoryOccupied,
        viewMode = viewMode,
        listState = listState,
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

/**
 * The value row's content: the value and the operation element.
 *
 * S3192: the row's width and top offset are supplied by the flavor's `CalculatorBody` in [placement],
 * because that is exactly where the two calculators differ; everything drawn inside the row is shared.
 */
@Composable
internal fun CalculatorValueRow(
    uiState: CalculatorUiState,
    placement: Modifier,
    verticalAlignment: Alignment.Vertical,
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
    Row(
        // S2273: the row is sized and placed by the glass, not by the frame. It does not scroll, so
        // `clip-check` judges it OFF-GLASS the moment a corner leaves the circle - there is no scroll
        // position that could redeem it - and both of its children were exactly that on 192 dp. Width
        // and top offset are the two halves of one statement and have to stay together: the square is
        // only whole while it sits in the band the ring inset leaves it.
        //
        // [TOUCH_TARGET] is the third part of that statement. Both children sit in this row, so each one
        // publishes a box of at least that size centred on the ROW's centre line: a row shorter than
        // the target reaches above its own top edge by the difference, which is how a box placed at
        // 28.8 dp came to be reported starting at 21.5 dp.
        modifier = placement.heightIn(min = TOUCH_TARGET),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
        verticalAlignment = verticalAlignment
    ) {
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
                // Whatever the operation element does not spend. S2493 divided this row by keypad
                // weights so the element would land under the outer keypad column; S2273 narrowed the
                // row to the glass, which parted the two anyway, so the element now takes a stated
                // width and the number keeps the rest.
                .weight(1f)
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
            modifier = Modifier.width(TOUCH_TARGET)
        )
    }
}

/**
 * S3192: the keypad's rows and the clear row, in order, for the flavor's scrolling column to hold.
 *
 * Shared so the two calculators differ only in where the column stands, never in what it carries.
 */
@Composable
internal fun CalculatorKeypadContent(
    onKey: (CalculatorKey) -> Unit,
    onLongKey: (CalculatorKey) -> Unit,
    onLeave: () -> Unit
) {
    keypadRows().forEach { row ->
        CalculatorKeyRow(cells = row, onKey = onKey, onLongKey = onLongKey)
    }
    ClearKeyRow(onKey = onKey, onLeave = onLeave)
}

/**
 * S2493: the clear key stands in a row of its own below every keypad row - two columns wide, against
 * the start edge, with a guard gap above it. The unused half below `=` carries the standard back
 * affordance, separated from both controls by the keypad gap.
 *
 * A row of its own and not a sixth cell in the last keypad row, because the guard the owner asked for
 * is vertical: the two columns left empty beside clear are the two `=` occupies in the row above, so a
 * finger that overshoots `=` crosses the gap instead of landing on a reset.
 *
 * S2007 criterion 7 measured this key's tap centre about 15 px outside a 240 px radius at maximum
 * scroll, which is what moved it out of the keypad in S2152. It is back in the scroll container but no
 * longer its last thing: [KEYPAD_TRAILING_SPACE] still follows, so the row scrolls to the middle of the
 * glass instead of resting against the rim where the circle narrows.
 */
@Composable
private fun ClearKeyRow(
    onKey: (CalculatorKey) -> Unit,
    onLeave: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnWidth = (maxWidth - KEY_GAP * (KEYPAD_COLUMNS - 1)) / KEYPAD_COLUMNS
        // The key swallows the gaps its own spanned columns used to be separated by, the same way a
        // spanned keypad cell does - otherwise it would stop short of the column it claims to reach.
        val clearWidth = columnWidth * CLEAR_COLUMN_SPAN + KEY_GAP * (CLEAR_COLUMN_SPAN - 1)
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(KEY_HEIGHT * CLEAR_ROW_GAP_FRACTION))
            Row(
                modifier = Modifier.fillMaxWidth(),
                // S3104: the one row on this screen that is NOT centred (owner ruling 2026-09-14).
                // Centring the row and then offsetting the affordance by two thirds of its own size
                // were the two things holding the back control against the right rim; both are gone,
                // so the row starts at the keypad's start edge and the affordance follows the clear
                // key across the ordinary key gap. That is short of the full affordance width the
                // owner asked for, and the clear key is why: the free space between the two controls
                // is less than one affordance, so a wider move would put the arrow under `C`.
                horizontalArrangement = Arrangement.spacedBy(KEY_GAP)
            ) {
                ClearKey(
                    modifier = Modifier.width(clearWidth),
                    onClick = { onKey(CalculatorKey.Clear) }
                )
                WearBackAffordance(
                    role = WearBackAffordanceRole.Back,
                    onClick = onLeave
                )
            }
        }
    }
}

/**
 * The clear key itself, sized by [ClearKeyRow].
 *
 * It keeps the destructive plate [keyColorsFor] gives it, because moving a key must not quietly restate
 * what its colour means (S2007 ADR-4).
 */
@Composable
private fun ClearKey(modifier: Modifier, onClick: () -> Unit) {
    val description = descriptionFor(CalculatorKey.Clear)
    RectangularButton(
        onClick = onClick,
        modifier = modifier
            .height(KEY_HEIGHT * CLEAR_KEY_HEIGHT_MULTIPLIER)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp),
        colors = keyColorsFor(CalculatorKey.Clear)
    ) {
        WearFitText(
            text = labelFor(CalculatorKey.Clear),
            style = labelStyleFor(CalculatorKey.Clear),
            // S2493: first position of its row, so the label hugs the end edge - which now points at
            // the middle of the glass, the part of a bottom-row key the round display never crops.
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
 * S2493: the operation is a transparent caption in its existing right-hand column rather than a
 * button-shaped plate. It preserves the same keypad-sized hit target and repeat-on-tap action while
 * making the current operation read as part of the display.
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
    WearFitText(
        text = glyphFor(operation),
        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
        modifier = modifier
            .height(KEY_HEIGHT)
            .clickable { onOperation(operation.symbol) }
            .semantics { contentDescription = description }
            // S2152: the glyph keeps its distance from the cell's own edge, the same way a hugged
            // key label does, rather than being treated as its own special case.
            .padding(horizontal = LABEL_HUG_PADDING),
        textAlign = TextAlign.Start
    )
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
                WearFitText(
                    text = label,
                    style = labelStyleFor(key),
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
 * palette these keys follow it instead of standing out of line. The content colour is not `onError`
 * (ADR-4): Wear's `onError` is a dark colour meant for a fully saturated error surface, and over a
 * translucent tint on a black background it would be close to unreadable.
 *
 * S2522: it is the scaffold's content colour rather than the palette's `onBackground`, because this
 * screen pins a black container and so does not follow the scheme's background. Under a light scheme
 * `onBackground` is near-black, which put the digits at roughly 1.2:1 against their own keypad.
 */
@Composable
private fun keyColorsFor(key: CalculatorKey): ButtonColors = when (key) {
    CalculatorKey.Clear,
    CalculatorKey.Backspace -> ButtonDefaults.buttonColors(
        backgroundColor = MaterialTheme.colors.error.copy(alpha = DESTRUCTIVE_TINT_ALPHA),
        contentColor = LocalContentColor.current
    )

    is CalculatorKey.Digit -> if (key.value % EVEN_DIVISOR == 0) {
        ButtonDefaults.secondaryButtonColors()
    } else {
        ButtonDefaults.buttonColors(
            backgroundColor = LocalContentColor.current.copy(alpha = ODD_DIGIT_TINT_ALPHA),
            contentColor = LocalContentColor.current
        )
    }

    is CalculatorKey.Operator -> if (
        key.operator == WearCalculatorEngine.Operator.PLUS ||
        key.operator == WearCalculatorEngine.Operator.TIMES
    ) {
        ButtonDefaults.primaryButtonColors()
    } else {
        ButtonDefaults.secondaryButtonColors()
    }

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
