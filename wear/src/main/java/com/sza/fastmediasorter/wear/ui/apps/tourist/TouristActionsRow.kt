package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R

private val ACTION_LABEL_SIZE_SP = 10.sp
private val ATHLETE_LABEL_SIZE_SP = 13.sp
private val ATHLETE_H_PADDING = 14.dp
private val ATHLETE_V_PADDING = 6.dp
private val ROW_SPACING = 6.dp
private val COLUMN_SPACING = 4.dp

/**
 * S3007 / S3015: Quick action buttons for resetting metrics and entering athlete mode. S3115 moved the
 * lock control onto the hero panel itself, so this row no longer carries it.
 */
@Composable
fun TouristActionsRow(
    onResetTrip: () -> Unit,
    onResetSteps: () -> Unit,
    onToggleAthleteMode: () -> Unit,
    onLaunchSos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(COLUMN_SPACING),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_SPACING, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onResetTrip,
                label = {
                    Text(
                        text = stringResource(R.string.wear_tourist_action_reset_trip),
                        fontSize = ACTION_LABEL_SIZE_SP,
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
            )
            CompactChip(
                onClick = onResetSteps,
                label = {
                    Text(
                        text = stringResource(R.string.wear_tourist_action_reset_steps),
                        fontSize = ACTION_LABEL_SIZE_SP,
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_SPACING, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onToggleAthleteMode,
                label = {
                    Text(
                        text = "🏃 " + stringResource(R.string.wear_tourist_athlete_mode),
                        fontSize = ATHLETE_LABEL_SIZE_SP,
                    )
                },
                colors = ChipDefaults.primaryChipColors(),
                contentPadding = PaddingValues(
                    horizontal = ATHLETE_H_PADDING,
                    vertical = ATHLETE_V_PADDING,
                ),
            )
        }
        // S3216: a row of its own rather than a fourth chip beside the two resets. The dashboard is
        // where the owner already is when something goes wrong outdoors, so the distress signal has to
        // be found without reading - and it must not be the neighbour of a button that zeroes a metric.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ROW_SPACING, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactChip(
                onClick = onLaunchSos,
                label = {
                    Text(
                        text = "🚨 " + stringResource(R.string.wear_tourist_action_sos),
                        fontSize = ATHLETE_LABEL_SIZE_SP,
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = colorResource(R.color.color_program_accent_scarlet),
                ),
                contentPadding = PaddingValues(
                    horizontal = ATHLETE_H_PADDING,
                    vertical = ATHLETE_V_PADDING,
                ),
            )
        }
    }
}
