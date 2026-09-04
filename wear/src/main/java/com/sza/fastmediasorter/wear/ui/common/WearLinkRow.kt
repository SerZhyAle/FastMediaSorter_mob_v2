package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * S2496: a row that reads as a link rather than as a button.
 *
 * A filled chip is the watch's vocabulary for "this performs an operation", which is why the About
 * screen's portal row was indistinguishable from the one that sends logs. Dropping the fill and
 * underlining the label restores the distinction; the row stays full width, so the tap target on a
 * round screen is unchanged.
 */
@Composable
fun WearLinkRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    // A Column, not two siblings: one ScalingLazyColumn item is a single slot, so a bare Chip and
    // Text would stack on top of each other - the same trap already documented in SendLogsRow.
    Column(modifier = modifier.fillMaxWidth()) {
        Chip(
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    color = MaterialTheme.colors.primary,
                    textDecoration = TextDecoration.Underline
                )
            },
            colors = ChipDefaults.childChipColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption1
            )
        }
    }
}
