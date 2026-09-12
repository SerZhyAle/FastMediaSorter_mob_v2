package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val TOGGLE_LABEL_MAX_LINES = 1
private val TOGGLE_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val TOGGLE_MIN_HEIGHT = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

/**
 * S2819: one short fixed enumeration as a single compact row - the smallest form a choice takes on the
 * watch.
 *
 * Why it exists beside [wearChoiceRows] rather than inside it: that builder renders a choice as list
 * ROWS, one option per row, or as grid cells whose count follows the caller's view mode. A three-value
 * choice that the wearer re-picks constantly should cost one row in total and must not change shape
 * when the content list behind it is switched to two or three columns (strategic §2). Those are
 * different layouts of the same data, so this is a second renderer, not a flag on the first.
 *
 * Owns no state: [selected] arrives from the caller's own state and a tap leaves through [onSelected].
 * The chosen cell is marked twice over - the primary chip colours and a `selected` semantics flag -
 * because colour alone carries neither in sunlight nor to TalkBack.
 */
@Composable
fun <T> WearSegmentedToggleRow(
    options: List<T>,
    selected: T?,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TOGGLE_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            SegmentedToggleCell(
                label = labelOf(option),
                isSelected = option == selected,
                onClick = { onSelected(option) }
            )
        }
    }
}

/**
 * The cell keeps the standard watch tap target as a MINIMUM rather than a fixed height: a label the
 * wearer's font scale grew has to push the row taller instead of being cropped (S2755).
 */
@Composable
private fun RowScope.SegmentedToggleCell(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    CompactChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = TOGGLE_LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = Modifier
            .weight(1f)
            .heightIn(min = TOGGLE_MIN_HEIGHT)
            .semantics { selected = isSelected },
        colors = if (isSelected) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        }
    )
}
