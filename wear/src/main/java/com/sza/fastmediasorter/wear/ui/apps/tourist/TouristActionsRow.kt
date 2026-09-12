package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

/**
 * S3007: Quick action buttons for resetting trip distance and session steps.
 */
@Composable
fun TouristActionsRow(
    onResetTrip: () -> Unit,
    onResetSteps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactChip(
            onClick = onResetTrip,
            label = { Text(stringResource(R.string.wear_tourist_action_reset_trip)) },
            colors = ChipDefaults.secondaryChipColors(),
        )
        CompactChip(
            onClick = onResetSteps,
            label = { Text(stringResource(R.string.wear_tourist_action_reset_steps)) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

