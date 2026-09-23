package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchParticipant
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearFitText

private val REGION_GAP = 2.dp
private val REGION_PADDING = 2.dp

/** Two regions per row once there are four of them; below that a region owns the full width. */
private const val QUAD_COLUMNS = 2
private const val QUAD_COUNT = 4
private const val PAIR_COUNT = 2

/** A button of a quarter-sized region still has to be pressable, so it never goes below this. */
private val BUTTON_MIN_HEIGHT = 28.dp

/**
 * The participant regions, one per independent measurement.
 *
 * Laid out inside the square the caller hands down rather than across the full width: on a round
 * display the corners of a full-width two-by-two grid stand off the glass, and a button that cannot be
 * pressed is worse than a smaller one that can (strategic §7).
 */
@Composable
internal fun WearStopwatchRegions(
    state: WearStopwatchState,
    nowMillis: Long,
    onStartOrLap: (Int) -> Unit,
    onStopOrReset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val participants = state.participants
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(REGION_GAP),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (participants.size) {
            QUAD_COUNT -> participants.chunked(QUAD_COLUMNS).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(REGION_GAP)
                ) {
                    pair.forEach { participant ->
                        RowRegion(participant, nowMillis, participants.size, onStartOrLap, onStopOrReset)
                    }
                }
            }

            else -> participants.forEach { participant ->
                ColumnRegion(participant, nowMillis, participants.size, onStartOrLap, onStopOrReset)
            }
        }
    }
}

@Composable
private fun RowScope.RowRegion(
    participant: WearStopwatchParticipant,
    nowMillis: Long,
    participantCount: Int,
    onStartOrLap: (Int) -> Unit,
    onStopOrReset: (Int) -> Unit
) {
    WearStopwatchRegion(
        participant = participant,
        nowMillis = nowMillis,
        participantCount = participantCount,
        onStartOrLap = onStartOrLap,
        onStopOrReset = onStopOrReset,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ColumnScope.ColumnRegion(
    participant: WearStopwatchParticipant,
    nowMillis: Long,
    participantCount: Int,
    onStartOrLap: (Int) -> Unit,
    onStopOrReset: (Int) -> Unit
) {
    WearStopwatchRegion(
        participant = participant,
        nowMillis = nowMillis,
        participantCount = participantCount,
        onStartOrLap = onStartOrLap,
        onStopOrReset = onStopOrReset,
        modifier = Modifier.fillMaxWidth().weight(1f)
    )
}

/**
 * One participant: its reading, its two buttons and how many laps it holds.
 *
 * The primary button's WORD carries the running state - `Lap` while it runs, `Start` while it does not -
 * so the two states are told apart without relying on colour (strategic §3.2).
 */
@Composable
private fun WearStopwatchRegion(
    participant: WearStopwatchParticipant,
    nowMillis: Long,
    participantCount: Int,
    onStartOrLap: (Int) -> Unit,
    onStopOrReset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val readingStyle = readingStyleFor(participantCount)
    val labelStyle = MaterialTheme.typography.caption2
    Column(
        modifier = modifier.padding(REGION_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (participantCount > 1) {
            Text(
                text = stringResource(R.string.wear_stopwatch_participant_short, participant.index + 1),
                style = labelStyle,
                maxLines = 1
            )
        }
        // S3362: the reading is the one thing on this screen that has to stay whole, and it is the one
        // thing that cannot get a bigger box - the regions are sized by the round glass. At a large
        // system font scale the widest form (H:MM:SS.hh at four participants, or display2 at one) was
        // cut mid-digit, which is Wear OS review item WO-V1. It shrinks to fit instead, keyed on the
        // LENGTH of the reading so a tick does not restart the search every frame.
        val reading = WearStopwatchTimeFormatter.format(participant.elapsedAt(nowMillis))
        WearFitText(
            text = reading,
            style = readingStyle,
            textAlign = TextAlign.Center,
            sizeKey = reading.length
        )
        if (participant.laps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.wear_stopwatch_laps_count, participant.laps.size),
                style = labelStyle,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(REGION_GAP)
        ) {
            RegionButton(
                label = if (participant.isRunning) {
                    stringResource(R.string.wear_stopwatch_lap)
                } else {
                    stringResource(R.string.wear_stopwatch_start)
                },
                onClick = { onStartOrLap(participant.index) },
                modifier = Modifier.weight(1f)
            )
            RegionButton(
                label = if (participant.isRunning) {
                    stringResource(R.string.wear_stopwatch_stop)
                } else {
                    stringResource(R.string.wear_stopwatch_reset)
                },
                onClick = { onStopOrReset(participant.index) },
                colorsAreSecondary = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RegionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colorsAreSecondary: Boolean = false
) {
    RectangularButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = BUTTON_MIN_HEIGHT),
        colors = if (colorsAreSecondary) {
            ButtonDefaults.secondaryButtonColors()
        } else {
            ButtonDefaults.primaryButtonColors()
        }
    ) {
        // S3362: same failure as the reading above, one size down - a four-region cell is about a
        // third of the glass wide and holds two of these, so a font scale of 1.24 turns "Reset" into
        // a cut word. The label shrinks inside the button rather than the button growing, because the
        // grid it sits in is sized by the display.
        WearFitText(text = label, style = MaterialTheme.typography.button)
    }
}

@Composable
private fun readingStyleFor(participantCount: Int): TextStyle = when (participantCount) {
    1 -> MaterialTheme.typography.display2
    PAIR_COUNT -> MaterialTheme.typography.title2
    else -> MaterialTheme.typography.title3
}
