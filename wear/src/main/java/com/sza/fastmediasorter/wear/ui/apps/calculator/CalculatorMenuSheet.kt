package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorFunction
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearChoiceGridFit
import com.sza.fastmediasorter.wear.ui.common.wearChoiceRows

private val TITLE_VERTICAL_PADDING = 12.dp
private val LIST_SIDE_PADDING = 8.dp

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

@Composable
fun CalculatorMenuSheet(
    memoryOccupied: Boolean,
    actions: CalculatorMenuActions,
    viewMode: WearViewMode = WearViewMode.GRID_3
) {
    val listState = rememberScalingLazyListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        val gridFit = WearChoiceGridFit(
            viewMode = viewMode,
            availableWidthDp = maxWidth.value.toInt(),
            fixedEnumeration = true
        )
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = LIST_SIDE_PADDING)
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
                gridFit = gridFit
            )

            item { MenuRow(label = stringResource(R.string.wear_calc_memory_add), onClick = actions.onMemoryAdd) }
            item {
                MenuRow(
                    label = stringResource(R.string.wear_calc_memory_subtract),
                    onClick = actions.onMemorySubtract
                )
            }
            // Recall and clear are offered only when the cell holds something: an empty memory has
            // nothing to recall, and offering it would promise a value that is not there.
            if (memoryOccupied) {
                item {
                    MenuRow(
                        label = stringResource(R.string.wear_calc_memory_recall),
                        onClick = actions.onMemoryRecall
                    )
                }
                item {
                    MenuRow(
                        label = stringResource(R.string.wear_calc_memory_clear),
                        onClick = actions.onMemoryClear
                    )
                }
            }

            item { MenuRow(label = stringResource(R.string.wear_calc_history), onClick = actions.onHistory) }
            item { MenuRow(label = stringResource(R.string.wear_calc_close), onClick = actions.onDismiss) }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
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
