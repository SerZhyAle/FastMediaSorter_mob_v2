package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorFunction
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearChoiceGridFit
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearChoiceRows

private val TITLE_VERTICAL_PADDING = 12.dp

/**
 * The single entrance to everything the keypad does not carry: every function, the memory cell and
 * the history (owner ruling 2026-08-19).
 *
 * An overlay rather than a navigation destination, so the watch's dismiss gesture still leaves the
 * calculator rather than closing the menu - the menu is closed by choosing something or by its own
 * close row, which is why that row exists.
 */
/**
 * S1966: the menu's callbacks travel as one object rather than eight parameters, the shape this
 * module already uses for a composable with a handful of actions - NetworkSourcesActions,
 * StreamsActions, VideoPlayerActions, AudioPlayerActions.
 */
data class CalculatorMenuActions(
    val onFunction: (WearCalculatorFunction) -> Unit,
    val onMemoryAdd: () -> Unit,
    val onMemorySubtract: () -> Unit,
    val onMemoryRecall: () -> Unit,
    val onMemoryClear: () -> Unit,
    val onHistory: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * S2152: the memory cell's four actions as one enumeration, so they can be laid out as a group.
 *
 * Each carries the calculator notation for what it does. The marker leads the label rather than
 * trailing it because a grid cell truncates at its end, so the one part that must survive a narrow
 * column is the part drawn first - and it is what tells the group apart with the colour filter on or
 * for a reader who does not separate the two tints (S2003).
 */
private enum class CalculatorMemoryAction(val marker: String) {
    ADD("M+"),
    SUBTRACT("M-"),
    RECALL("MR"),
    CLEAR("MC")
}

/** S2152: history and close, gridded together so the menu ends in a row rather than in two chips. */
private enum class CalculatorMenuUtility { HISTORY, CLOSE }

@Composable
fun CalculatorMenuSheet(
    memoryOccupied: Boolean,
    actions: CalculatorMenuActions,
    viewMode: WearViewMode
) {
    val listState = rememberWearListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val gridFit = WearChoiceGridFit(
            viewMode = viewMode,
            availableWidthDp = maxWidth.value.toInt(),
            fixedEnumeration = true
        )
        // S2152 ADR-2: the memory group's own warm tone, declared as its own resource. S2007 ADR-4
        // holds - the theme's error colour still means a destructive action and nothing else, and no
        // row here is drawn from it.
        val memoryColors = ChipDefaults.chipColors(
            backgroundColor = colorResource(R.color.wear_calc_memory_tint),
            contentColor = MaterialTheme.colors.onSurface
        )
        val functionColors = ChipDefaults.chipColors(
            backgroundColor = colorResource(R.color.wear_calc_function_tint),
            contentColor = MaterialTheme.colors.onSurface
        )
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_calc_menu),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TITLE_VERTICAL_PADDING)
                )
            }

            wearChoiceRows(
                options = WearCalculatorFunction.entries,
                selected = null,
                labelOf = { stringResource(labelResFor(it)) },
                onSelected = actions.onFunction,
                gridFit = gridFit,
                unselectedColors = functionColors
            )

            wearChoiceRows(
                options = memoryActionsFor(memoryOccupied),
                selected = null,
                labelOf = { action -> "${action.marker} ${stringResource(memoryLabelResFor(action))}" },
                onSelected = { action -> actions.run(action) },
                gridFit = gridFit,
                unselectedColors = memoryColors
            )

            wearChoiceRows(
                options = CalculatorMenuUtility.entries,
                selected = null,
                labelOf = { stringResource(utilityLabelResFor(it)) },
                onSelected = { utility -> actions.run(utility) },
                gridFit = gridFit
            )
        }
    }
}

/**
 * Recall and clear are offered only when the cell holds something: an empty memory has nothing to
 * recall, and offering it would promise a value that is not there.
 */
private fun memoryActionsFor(memoryOccupied: Boolean): List<CalculatorMemoryAction> =
    if (memoryOccupied) {
        CalculatorMemoryAction.entries
    } else {
        listOf(CalculatorMemoryAction.ADD, CalculatorMemoryAction.SUBTRACT)
    }

private fun CalculatorMenuActions.run(action: CalculatorMemoryAction) {
    when (action) {
        CalculatorMemoryAction.ADD -> onMemoryAdd()
        CalculatorMemoryAction.SUBTRACT -> onMemorySubtract()
        CalculatorMemoryAction.RECALL -> onMemoryRecall()
        CalculatorMemoryAction.CLEAR -> onMemoryClear()
    }
}

private fun CalculatorMenuActions.run(utility: CalculatorMenuUtility) {
    when (utility) {
        CalculatorMenuUtility.HISTORY -> onHistory()
        CalculatorMenuUtility.CLOSE -> onDismiss()
    }
}

private fun memoryLabelResFor(action: CalculatorMemoryAction): Int = when (action) {
    CalculatorMemoryAction.ADD -> R.string.wear_calc_memory_add
    CalculatorMemoryAction.SUBTRACT -> R.string.wear_calc_memory_subtract
    CalculatorMemoryAction.RECALL -> R.string.wear_calc_memory_recall
    CalculatorMemoryAction.CLEAR -> R.string.wear_calc_memory_clear
}

private fun utilityLabelResFor(utility: CalculatorMenuUtility): Int = when (utility) {
    CalculatorMenuUtility.HISTORY -> R.string.wear_calc_history
    CalculatorMenuUtility.CLOSE -> R.string.wear_calc_close
}

private fun labelResFor(function: WearCalculatorFunction): Int = when (function) {
    WearCalculatorFunction.SINE -> R.string.wear_calc_fn_sine
    WearCalculatorFunction.COSINE -> R.string.wear_calc_fn_cosine
    WearCalculatorFunction.TANGENT -> R.string.wear_calc_fn_tangent
    WearCalculatorFunction.COTANGENT -> R.string.wear_calc_fn_cotangent
    WearCalculatorFunction.SQUARE_ROOT -> R.string.wear_calc_fn_square_root
    WearCalculatorFunction.CUBE_ROOT -> R.string.wear_calc_fn_cube_root
    WearCalculatorFunction.SQUARE -> R.string.wear_calc_fn_square
    WearCalculatorFunction.RECIPROCAL -> R.string.wear_calc_fn_reciprocal
    WearCalculatorFunction.LOG10 -> R.string.wear_calc_fn_log10
    WearCalculatorFunction.NATURAL_LOG -> R.string.wear_calc_fn_natural_log
    WearCalculatorFunction.FACTORIAL -> R.string.wear_calc_fn_factorial
    WearCalculatorFunction.PI -> R.string.wear_calc_fn_pi
    WearCalculatorFunction.PERCENT -> R.string.wear_calc_fn_percent
    WearCalculatorFunction.ROUND -> R.string.wear_calc_fn_round
}
