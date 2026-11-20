package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import timber.log.Timber

private val ATHLETE_H_PADDING = 14.dp
private val ATHLETE_V_PADDING = 6.dp
private val ROW_SPACING = 6.dp
private val COLUMN_SPACING = 4.dp
private val RESET_CHIP_H_PADDING = 8.dp
private const val RESET_LABEL_MAX_LINES = 2

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
    LaunchedEffect(Unit) {
        Timber.d("S4745: tourist reset chips shown")
    }
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
            ResetChip(
                onClick = onResetTrip,
                label = stringResource(R.string.wear_tourist_action_reset_trip),
            )
            ResetChip(
                onClick = onResetSteps,
                label = stringResource(R.string.wear_tourist_action_reset_steps),
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
                        style = MaterialTheme.typography.caption2,
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
                        style = MaterialTheme.typography.caption2,
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

/**
 * One of the two reset chips. Equal weights split the 153.6 dp content box of a 192 dp round watch in
 * half: without them the first chip took its natural width and the second got the remainder, which
 * wrapped "Reset Steps" onto a second line inside a one-line button. The label may still use two
 * centred lines, so a long translation wraps in both chips alike instead of overflowing one.
 */
@Composable
private fun RowScope.ResetChip(
    onClick: () -> Unit,
    label: String,
) {
    CompactChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
                maxLines = RESET_LABEL_MAX_LINES,
            )
        },
        colors = ChipDefaults.secondaryChipColors(),
        contentPadding = PaddingValues(horizontal = RESET_CHIP_H_PADDING),
        modifier = Modifier.weight(1f),
    )
}
