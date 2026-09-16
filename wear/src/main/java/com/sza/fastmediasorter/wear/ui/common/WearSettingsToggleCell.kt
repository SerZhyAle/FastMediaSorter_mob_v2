package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import com.sza.fastmediasorter.wear.util.GridColumnFit

// Read from the column rule, not written down again: the same threshold that decides how many
// columns fit is what a cell's control must not fall below, so one number owns both answers.
private val CELL_BUTTON_SIZE = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
private val CELL_ICON_SIZE = 24.dp

/**
 * One toggle setting with a small state glyph on the left and label on the right.
 */
@Composable
fun WearSettingsToggleCell(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    accessibilityLabel: String = label,
    narrow: Boolean = false
) {
    val labelStyle = MaterialTheme.typography.caption2
    val icon = if (radio) {
        ToggleChipDefaults.radioIcon(checked)
    } else {
        ToggleChipDefaults.switchIcon(checked)
    }
    val tint = if (checked) {
        WearAppTheme.colors.toggleOn
    } else {
        WearAppTheme.colors.toggleOff
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CELL_BUTTON_SIZE)
            .clickable(
                onClick = {
                    onToggle()
                }
            )
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                selected = checked
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(CELL_ICON_SIZE)
        )
        Text(
            text = label,
            style = labelStyle,
            maxLines = if (narrow) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
        )
    }
}
