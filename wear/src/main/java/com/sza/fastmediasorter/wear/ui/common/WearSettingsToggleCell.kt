package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
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

/**
 * One toggle setting, drawn either as a full-width chip or as a compact horizontal cell in a row.
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CELL_BUTTON_SIZE)
            .clickable(onClick = onToggle)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                selected = checked
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(CELL_ICON_SIZE)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption3
        )
    }
}
