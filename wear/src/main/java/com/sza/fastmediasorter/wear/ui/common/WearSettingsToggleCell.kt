package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.util.GridColumnFit

// Read from the column rule, not written down again: the same threshold that decides how many
// columns fit is what a cell's control must not fall below, so one number owns both answers.
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val CELL_LABEL_TOP_PADDING = 2.dp
private const val NARROW_LABEL_MAX_LINES = 2

/**
 * One toggle setting, drawn either as a full-width chip or as a narrow cell in a row.
 *
 * The two forms are not the same control at two sizes. Measured on a 480x480 watch (226 dp) 2026-08-25:
 * a `ToggleChip` in half a row keeps its switch and its padding at full size, which leaves the label
 * about six characters per line - "Enable Audio" rendered as three clipped lines, and the three
 * view-mode chips at three columns lost their labels outright. A chip cannot be narrow, so the narrow
 * form stacks the control over a two-line label instead, the same shape the settings menu itself uses.
 * The label is never truncated and never ellipsised; it wraps to at most two lines (strategic S1949 6.1).
 */
@Composable
fun WearSettingsToggleCell(
    label: String,
    checked: Boolean,
    narrow: Boolean,
    onToggle: () -> Unit,
    radio: Boolean = false,
    accessibilityLabel: String = label
) {
    val icon = if (radio) {
        ToggleChipDefaults.radioIcon(checked)
    } else {
        ToggleChipDefaults.switchIcon(checked)
    }

    if (!narrow) {
        ToggleChip(
            checked = checked,
            onCheckedChange = { onToggle() },
            label = { Text(text = label) },
            toggleControl = { Icon(imageVector = icon, contentDescription = accessibilityLabel) },
            colors = ToggleChipDefaults.toggleChipColors(),
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    Column(
        // Control and label are one tap target and one accessibility stop. Splitting them would put
        // a 48 dp button next to an inert caption of the same width, which reads as two stops saying
        // the same words and wastes half the cell the layout just fought to earn.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .clearAndSetSemantics { contentDescription = accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RectangularButton(
            onClick = onToggle,
            modifier = Modifier.size(CELL_BUTTON_SIZE),
            colors = if (checked) {
                ButtonDefaults.primaryButtonColors()
            } else {
                ButtonDefaults.secondaryButtonColors()
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(CELL_ICON_SIZE)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.caption3,
            maxLines = NARROW_LABEL_MAX_LINES,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CELL_LABEL_TOP_PADDING)
        )
    }
}
