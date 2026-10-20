package com.sza.fastmediasorter.wear.ui.apps.clipboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

private val FAILURE_TOP_PADDING = 4.dp
private val CHIP_ICON_SIZE = 16.dp

/**
 * S4381: "Open in.." under the object it acts on (strategic ADR-1): the system clipboard text under "Send to phone",
 * and the value received from the server under its result line.
 *
 * A failed launch is a line of text under the chip that started it, the way the send outcome is: the watch has no
 * Snackbar host, and a chip that fails silently is the one outcome criterion 11 rules out.
 */
@Composable
internal fun OpenInChip(
    enabled: Boolean,
    failedAppLabel: String?,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CompactChip(
            onClick = onClick,
            enabled = enabled,
            label = { Text(stringResource(R.string.wear_clipboard_open_in_action)) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(CHIP_ICON_SIZE),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.secondaryChipColors(),
        )
        if (failedAppLabel != null) {
            Text(
                text = stringResource(R.string.wear_open_in_failed, failedAppLabel),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FAILURE_TOP_PADDING),
                textAlign = TextAlign.Center,
            )
        }
    }
}
