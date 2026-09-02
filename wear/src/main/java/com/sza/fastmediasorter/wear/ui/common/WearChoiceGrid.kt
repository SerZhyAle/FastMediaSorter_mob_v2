package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private const val GRID_LABEL_MAX_LINES = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CHOICE_ICON_SIZE = 20.dp
private val GRID_CELL_HEIGHT = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

/**
 * What decides a choice list's column count: the caller's active [viewMode], the width it measured
 * for the caller's own `BoxWithConstraints` (S2133 - so no caller repeats `GridColumnFit`'s
 * arithmetic for a choice list of its own), and whether this particular set is even allowed to grid.
 *
 * [fixedEnumeration] is the set's nature, not the surface's type (strategic ADR-3): a short fixed
 * enumeration - a view mode, a sort order, a menu of functions - may grid; a set whose labels come from
 * data and can be arbitrarily long - a language or a topic - stays one column regardless of [viewMode].
 * S1947 measured that a grid of long values is unusable on a round screen, and that measurement does
 * not stop being true because a different dialog is asking now.
 */
data class WearChoiceGridFit(
    val viewMode: WearViewMode,
    val availableWidthDp: Int,
    val fixedEnumeration: Boolean = true
)

/**
 * S2133: one option list, rendered as [gridFit] asks - a column of chips for `LIST`, or safe grid rows
 * for `GRID_2` / `GRID_3`.
 *
 * A `ScalingLazyListScope` extension rather than a dialog of its own (strategic 5.1): the caller keeps
 * its own `ScalingLazyColumn`, title item, [wearScreenInsets] and [WearGridScalingParams], so an
 * existing dialog gains columns instead of being replaced by a second one. Owns no state - [selected]
 * and [gridFit] arrive from the caller's own state and [onSelected] is how a pick leaves; the caller's
 * `ScalingLazyListState` is untouched here because the initial working row has to be decided before
 * composition, which is the caller's job, not this extension's (S1945).
 *
 * The chosen row is marked twice over, in both the list and the grid shape: a check glyph, and a
 * `selected` semantics flag. Colour alone would not carry the choice on a grid cell either (strategic
 * 3.1).
 *
 * [unselectedColors] is for a caller that lays several groups of rows into one list and needs them to
 * read apart (S2152). Left null it is the secondary chip every other caller already draws, so a group
 * has to ask for a tone rather than inherit one.
 */
fun <T> ScalingLazyListScope.wearChoiceRows(
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    gridFit: WearChoiceGridFit,
    key: ((T) -> Any)? = null,
    unselectedColors: ChipColors? = null
) {
    val columns = if (gridFit.fixedEnumeration) {
        GridColumnFit.columnsFor(gridFit.viewMode, gridFit.availableWidthDp)
    } else {
        SINGLE_COLUMN
    }

    if (columns == SINGLE_COLUMN) {
        items(options, key = key) { option ->
            WearChoiceListChip(
                label = labelOf(option),
                isSelected = option == selected,
                onClick = { onSelected(option) },
                unselectedColors = unselectedColors
            )
        }
    } else {
        items(options.chunked(columns)) { rowOptions ->
            WearChoiceGridRow(
                options = rowOptions,
                columns = columns,
                selected = selected,
                labelOf = labelOf,
                onSelected = onSelected,
                unselectedColors = unselectedColors
            )
        }
    }
}

@Composable
private fun WearChoiceListChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    unselectedColors: ChipColors?
) {
    Chip(
        onClick = onClick,
        label = { Text(text = label, maxLines = GRID_LABEL_MAX_LINES, overflow = TextOverflow.Ellipsis) },
        icon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    // The row's own `selected` flag is what TalkBack reads; describing the glyph too
                    // would announce the same fact a second time.
                    contentDescription = null,
                    modifier = Modifier.size(CHOICE_ICON_SIZE)
                )
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = isSelected },
        colors = chipColorsFor(isSelected, unselectedColors)
    )
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun <T> WearChoiceGridRow(
    options: List<T>,
    columns: Int,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    unselectedColors: ChipColors?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Chip(
                onClick = { onSelected(option) },
                label = {
                    Text(
                        text = labelOf(option),
                        maxLines = GRID_LABEL_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(CHOICE_ICON_SIZE)
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .height(GRID_CELL_HEIGHT)
                    .semantics { this.selected = isSelected },
                colors = chipColorsFor(isSelected, unselectedColors)
            )
        }
        repeat(columns - options.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** The selected row keeps the primary chip in every group - a group's tone marks it, not its choice. */
@Composable
private fun chipColorsFor(isSelected: Boolean, unselectedColors: ChipColors?): ChipColors = when {
    isSelected -> ChipDefaults.primaryChipColors()
    unselectedColors != null -> unselectedColors
    else -> ChipDefaults.secondaryChipColors()
}
