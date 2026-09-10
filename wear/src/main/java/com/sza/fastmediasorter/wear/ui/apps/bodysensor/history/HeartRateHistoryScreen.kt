package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 8.dp
private val ROW_VERTICAL_PADDING = 4.dp

/**
 * S2808: the heart-rate measurement history screen.
 *
 * Lists all stored measurements newest-first with date, time, and BPM. A clear-all chip
 * at the bottom deletes every entry in one action.
 */
@Composable
fun HeartRateHistoryScreen(
    viewModel: HeartRateHistoryViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Timber.d("S2808: heart rate history screen opened")

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            centered = true
        ) {
            item { ScreenTitle() }
            if (state.isEmpty) {
                item { EmptyMessage() }
            } else {
                items(state.entries) { entry -> HistoryRow(entry) }
                item { ClearChip(onClick = { viewModel.clearHistory() }) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.body_sensor_history_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EmptyMessage() {
    Text(
        text = stringResource(R.string.body_sensor_history_empty),
        style = MaterialTheme.typography.body1,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun HistoryRow(entry: HeartRateHistoryEntry) {
    val dateTime = formatTimestamp(entry.timestampMillis)
    Text(
        text = stringResource(R.string.body_sensor_history_row, dateTime, entry.bpm),
        style = MaterialTheme.typography.body1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ROW_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ClearChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.body_sensor_history_clear)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * S2795: the measurement system decides the field order and the clock length here, not the locale's
 * short form - a reading is compared against the ones above it, so the order has to be the same one
 * the rest of the app shows.
 */
@Composable
private fun formatTimestamp(timestampMillis: Long): String =
    LocalWearDateTimeFormatter.current.formatDateTime(timestampMillis, LocalWearUnitSystem.current)
