package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import com.sza.fastmediasorter.wear.R

private val BAR_VERTICAL_PADDING = 8.dp
private val COMMAND_BUTTON_SIZE = 48.dp
private val COMMAND_ICON_SIZE = 24.dp

/**
 * Screen-wide commands of the home screen, drawn under the section content.
 *
 * Settings lives here rather than among the sections so it neither competes with content for space
 * nor moves when the view mode changes - the owner ruling recorded in the strategic spec.
 */
@Composable
fun HomeCommandBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BAR_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onSettingsClick,
            modifier = Modifier.size(COMMAND_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings),
                modifier = Modifier.size(COMMAND_ICON_SIZE)
            )
        }
    }
}
