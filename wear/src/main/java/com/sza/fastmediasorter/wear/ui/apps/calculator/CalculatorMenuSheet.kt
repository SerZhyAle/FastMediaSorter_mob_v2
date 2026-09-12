package com.sza.fastmediasorter.wear.ui.apps.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ButtonColors
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.calculator.WearCalculatorFunction
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearFitText
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit

private val TITLE_VERTICAL_PADDING = 12.dp
private val TILE_HEIGHT = 36.dp
private val TILE_GAP = 4.dp
private const val ODD_ALPHA = 0.45f

/**
 * The single entrance to everything the keypad does not carry: every function, the memory cell and
 * the history (owner ruling 2026-08-19).
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

private enum class CalculatorMemoryAction(val marker: String) {
    ADD("M+"),
    SUBTRACT("M-"),
    RECALL("MR"),
    CLEAR("MC")
}

private enum class CalculatorMenuUtility { HISTORY, CLOSE }

/**
 * @param listState hoisted so the calculator's Scaffold can point its indicator at this menu while the
 * menu is what the wearer is scrolling - the keypad behind it is standing still (S2754).
 */
@Composable
fun CalculatorMenuSheet(
    memoryOccupied: Boolean,
    actions: CalculatorMenuActions,
    viewMode: WearViewMode,
    listState: ScalingLazyListState = rememberWearListState()
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        val functionColorsBase = colorResource(R.color.wear_calc_function_tint)
        val memoryColorsBase = colorResource(R.color.wear_calc_memory_tint)

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

            renderFunctionTiles(columns, functionColorsBase, actions.onFunction)
            renderMemoryTiles(columns, memoryColorsBase, memoryActionsFor(memoryOccupied), actions)
            renderUtilityTiles(columns, actions)
        }
    }
}

private fun ScalingLazyListScope.renderFunctionTiles(
    columns: Int,
    baseColor: Color,
    onFunction: (WearCalculatorFunction) -> Unit
) {
    val functionRows = WearCalculatorFunction.entries.chunked(columns)
    functionRows.forEachIndexed { rowIndex, rowFunctions ->
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TILE_GAP / 2),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP)
            ) {
                rowFunctions.forEachIndexed { colIndex, function ->
                    val isOdd = (rowIndex + colIndex) % 2 != 0
                    val bgColor = if (isOdd) baseColor.copy(alpha = ODD_ALPHA) else baseColor
                    val colors = ButtonDefaults.buttonColors(
                        backgroundColor = bgColor,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                    val label = stringResource(labelResFor(function))
                    TileButton(
                        label = label,
                        onClick = { onFunction(function) },
                        colors = colors
                    )
                }
            }
        }
    }
}

private fun ScalingLazyListScope.renderMemoryTiles(
    columns: Int,
    baseColor: Color,
    memoryActions: List<CalculatorMemoryAction>,
    actions: CalculatorMenuActions
) {
    val memoryRows = memoryActions.chunked(columns)
    memoryRows.forEachIndexed { rowIndex, rowMemory ->
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TILE_GAP / 2),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP)
            ) {
                rowMemory.forEachIndexed { colIndex, action ->
                    val isOdd = (rowIndex + colIndex) % 2 != 0
                    val bgColor = if (isOdd) baseColor.copy(alpha = ODD_ALPHA) else baseColor
                    val colors = ButtonDefaults.buttonColors(
                        backgroundColor = bgColor,
                        contentColor = MaterialTheme.colors.onSurface
                    )
                    val label = "${action.marker} ${stringResource(memoryLabelResFor(action))}"
                    TileButton(
                        label = label,
                        onClick = { actions.run(action) },
                        colors = colors
                    )
                }
            }
        }
    }
}

private fun ScalingLazyListScope.renderUtilityTiles(
    columns: Int,
    actions: CalculatorMenuActions
) {
    val utilityRows = CalculatorMenuUtility.entries.chunked(columns)
    utilityRows.forEachIndexed { rowIndex, rowUtility ->
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TILE_GAP / 2),
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP)
            ) {
                rowUtility.forEachIndexed { colIndex, utility ->
                    val isOdd = (rowIndex + colIndex) % 2 != 0
                    val primary = MaterialTheme.colors.primary
                    val bgColor = if (isOdd) primary.copy(alpha = 0.5f) else primary
                    val colors = ButtonDefaults.buttonColors(
                        backgroundColor = bgColor,
                        contentColor = MaterialTheme.colors.onPrimary
                    )
                    val label = stringResource(utilityLabelResFor(utility))
                    TileButton(
                        label = label,
                        onClick = { actions.run(utility) },
                        colors = colors
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TileButton(
    label: String,
    onClick: () -> Unit,
    colors: ButtonColors
) {
    RectangularButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(TILE_HEIGHT)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(4.dp),
        colors = colors
    ) {
        WearFitText(
            text = label,
            style = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

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
