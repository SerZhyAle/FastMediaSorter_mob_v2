package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorFunction
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private val TITLE_VERTICAL_PADDING = 12.dp
private val LIST_SIDE_PADDING = 8.dp
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp

/** S1942: the same interactive minimum the keypad respects, so a grid cell stays as hittable as a key. */
private val GRID_CELL_HEIGHT = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

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
    actions: CalculatorMenuActions
) {
    val listState = rememberScalingLazyListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // S1942: the width decides, and nothing else - no setting and no stored mode (owner,
        // 2026-08-22). Three columns are requested and the shared fit rule drops to fewer on a narrow
        // watch rather than shrinking a cell under the interactive minimum.
        val columns = GridColumnFit.columnsFor(WearViewMode.GRID_3, maxWidth.value.toInt())
        Timber.d("S1942: menu grid columns=$columns width=${maxWidth.value}")

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

            // The functions are the grid; the rows below it are not. A close button in a grid cell is
            // harder to hit, not easier, and the owner asked for the functions specifically.
            items(WearCalculatorFunction.entries.toList().chunked(columns)) { row ->
                FunctionGridRow(
                    functions = row,
                    columns = columns,
                    onFunction = { function -> actions.onFunction(function) },
                )
            }

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

/**
 * S1942: one row of the function grid.
 *
 * The last row is padded with empty weight so a short final row keeps the same cell width as the rows
 * above it - without it, two leftover functions would stretch to half the screen each and stop looking
 * like the same kind of thing.
 */
@Composable
private fun FunctionGridRow(
    functions: List<WearCalculatorFunction>,
    columns: Int,
    onFunction: (WearCalculatorFunction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
    ) {
        functions.forEach { function ->
            Chip(
                onClick = { onFunction(function) },
                label = { Text(text = stringResource(labelResFor(function)), maxLines = 1) },
                modifier = Modifier
                    .weight(1f)
                    .height(GRID_CELL_HEIGHT),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        repeat(columns - functions.size) {
            Spacer(modifier = Modifier.weight(1f))
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
