package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.RectangularButton

private val BAR_VERTICAL_PADDING = 8.dp
private val COMMAND_BUTTON_SIZE = 48.dp
private val COMMAND_ICON_SIZE = 24.dp
private val COMMAND_GAP = 8.dp

/**
 * Screen-wide commands of the home screen, drawn under the section content.
 *
 * They live here rather than among the sections so they neither compete with content for space nor
 * move when the view mode changes - the owner ruling recorded in the strategic spec.
 *
 * S1975: the order and the spacing are decided in this row, never at the call site, so a future
 * command stays a one-line change and cannot silently reorder the ones that already exist. Settings
 * keeps the first position because focus traversal follows the row and the frequent command has to
 * be met first (ADR-2). The commands wear the same secondary colours: making one of them the loudest
 * target of the row is precisely the accidental tap the risk register asks not to invite (ADR-4).
 *
 * S2472: the close command left this row for the edge affordance, which now owns closing (and
 * minimizing) from the same place every other screen keeps its back control.
 */
@Composable
fun HomeCommandBar(
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BAR_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(COMMAND_GAP, Alignment.CenterHorizontally)
    ) {
        CommandButton(
            icon = Icons.Filled.Settings,
            labelRes = R.string.settings,
            onClick = onSettingsClick
        )
    }
}

/**
 * One command of the bar. Extracted so size, colours and the label-as-description rule are stated
 * once: two copies are how the commands would drift into different targets and different weights.
 */
@Composable
private fun CommandButton(
    icon: ImageVector,
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    RectangularButton(
        onClick = onClick,
        modifier = Modifier.size(COMMAND_BUTTON_SIZE),
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            modifier = Modifier.size(COMMAND_ICON_SIZE)
        )
    }
}
