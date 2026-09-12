package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

/**
 * S3007 / S3015: Quick action buttons for resetting metrics, toggling athlete mode and locking screen.
 */
@Composable
fun TouristActionsRow(
    onResetTrip: () -> Unit,
    onResetSteps: () -> Unit,
    onToggleAthleteMode: () -> Unit,
    onLockScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onResetTrip,
                label = { Text(stringResource(R.string.wear_tourist_action_reset_trip), fontSize = 10.sp) },
                colors = ChipDefaults.secondaryChipColors(),
            )
            CompactChip(
                onClick = onResetSteps,
                label = { Text(stringResource(R.string.wear_tourist_action_reset_steps), fontSize = 10.sp) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onToggleAthleteMode,
                label = { Text("🏃 " + stringResource(R.string.wear_tourist_athlete_mode), fontSize = 10.sp) },
                colors = ChipDefaults.primaryChipColors(),
            )
            CompactChip(
                onClick = onLockScreen,
                label = { Text("🔒 " + stringResource(R.string.wear_tourist_lock_screen), fontSize = 10.sp) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
}
