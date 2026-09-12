package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

private val TITLE_VERTICAL_PADDING = 12.dp

/**
 * Everything that acts on the whole measurement, plus the participant count and the result page.
 *
 * These actions live here rather than beside the regions on purpose: a mis-tap on the main surface must
 * not be able to stop four measurements at once (strategic §5).
 */
data class WearStopwatchMenuActions(
    val onStartAll: () -> Unit,
    val onStopAll: () -> Unit,
    val onResetAll: () -> Unit,
    val onParticipantCountSelected: (Int) -> Unit,
    val onResult: () -> Unit,
    val onDismiss: () -> Unit
)

@Composable
fun WearStopwatchMenuSheet(
    participantCount: Int,
    actions: WearStopwatchMenuActions,
    listState: ScalingLazyListState = rememberWearListState()
) {
    WearListColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item {
            Text(
                text = stringResource(R.string.wear_app_stopwatch),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TITLE_VERTICAL_PADDING)
            )
        }
        item { MenuChip(R.string.wear_stopwatch_start_all, actions.onStartAll) }
        item { MenuChip(R.string.wear_stopwatch_stop_all, actions.onStopAll) }
        item { MenuChip(R.string.wear_stopwatch_reset_all, actions.onResetAll) }
        item {
            Text(
                text = stringResource(R.string.wear_stopwatch_participants),
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = TITLE_VERTICAL_PADDING)
            )
        }
        items(WearStopwatchState.ALLOWED_COUNTS.size) { position ->
            val count = WearStopwatchState.ALLOWED_COUNTS[position]
            Chip(
                onClick = { actions.onParticipantCountSelected(count) },
                label = { Text(text = count.toString()) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (count == participantCount) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                }
            )
        }
        item { MenuChip(R.string.wear_stopwatch_result, actions.onResult) }
        item { MenuChip(R.string.wear_state_back, actions.onDismiss) }
    }
}

@Composable
private fun MenuChip(@StringRes labelRes: Int, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}
