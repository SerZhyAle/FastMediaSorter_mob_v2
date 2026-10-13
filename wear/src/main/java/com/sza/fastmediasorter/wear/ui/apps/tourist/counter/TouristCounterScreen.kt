package com.sza.fastmediasorter.wear.ui.apps.tourist.counter

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristCounterRecord
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristCounterState
import com.sza.fastmediasorter.wear.ui.common.StandardWearAlertDialog
import com.sza.fastmediasorter.wear.ui.common.StandardWearChip
import com.sza.fastmediasorter.wear.ui.common.StandardWearToggleChip
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val PLUS_BUTTON_SIZE = 72.dp
private val MINUS_BUTTON_SIZE = 40.dp
private val ROW_GAP = 8.dp
private val STEP_GAP = 4.dp

// Narrows the step row to two chips across: three across reached the round glass at the
// second row (measured on the large-round AVD, 2026-10-13 - +50 and +1000 were cut by the edge).
private val EXTRA_STEPS_INSET = 40.dp

private const val HISTORY_TIME_FLAGS =
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH

/**
 * S4185: the watch Tourist tally counter - the count, a large +1 beside a small -1, the optional larger
 * steps, the starting number, a confirmed reset and the dated history of finished counts.
 */
@Composable
fun TouristCounterScreen(viewModel: TouristCounterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberWearListState(positionKey = WearRoutes.TOURIST_COUNTER)
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val requestStartValue = rememberWearRenameInput(hintRes = R.string.wear_tourist_counter_start) { text ->
        if (!viewModel.setStartValue(text)) {
            Toast.makeText(context, R.string.wear_tourist_counter_start_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            counterControls(
                state = state,
                onAdd = viewModel::add,
                onExtraStepsChange = viewModel::setExtraStepsVisible,
                onStartValue = { requestStartValue(state.startValue.toString()) },
                onReset = { confirmReset = true },
            )
            counterHistory(history = state.history, onClear = { confirmClearHistory = true })
        }
    }

    StandardWearAlertDialog(
        show = confirmReset,
        title = stringResource(R.string.wear_tourist_counter_reset_confirm),
        message = stringResource(R.string.wear_tourist_counter_reset_message),
        confirmLabel = stringResource(R.string.wear_tourist_counter_reset),
        isDestructive = true,
        onConfirm = {
            confirmReset = false
            viewModel.reset()
        },
        onDismissRequest = { confirmReset = false },
    )
    StandardWearAlertDialog(
        show = confirmClearHistory,
        title = stringResource(R.string.wear_tourist_counter_clear_history_confirm),
        confirmLabel = stringResource(R.string.wear_tourist_counter_clear_history),
        isDestructive = true,
        onConfirm = {
            confirmClearHistory = false
            viewModel.clearHistory()
        },
        onDismissRequest = { confirmClearHistory = false },
    )
}

private fun ScalingLazyListScope.counterControls(
    state: WearTouristCounterState,
    onAdd: (Long) -> Unit,
    onExtraStepsChange: (Boolean) -> Unit,
    onStartValue: () -> Unit,
    onReset: () -> Unit,
) {
    item {
        Text(
            text = stringResource(R.string.wear_tourist_counter),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Text(
            text = state.value.toString(),
            style = MaterialTheme.typography.display2,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    item { CounterTapRow(value = state.value, onAdd = onAdd) }
    item {
        StandardWearToggleChip(
            label = stringResource(R.string.wear_tourist_counter_extra_steps),
            checked = state.extraStepsVisible,
            onCheckedChange = onExtraStepsChange,
        )
    }
    if (state.extraStepsVisible) {
        item { CounterExtraSteps(onAdd = onAdd) }
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_tourist_counter_start),
            secondaryLabel = state.startValue.toString(),
            onClick = onStartValue,
            primary = false,
        )
    }
    item {
        StandardWearChip(
            label = stringResource(R.string.wear_tourist_counter_reset),
            onClick = onReset,
            primary = false,
        )
    }
}

private fun ScalingLazyListScope.counterHistory(
    history: List<WearTouristCounterRecord>,
    onClear: () -> Unit,
) {
    item {
        Text(
            text = stringResource(R.string.wear_tourist_counter_history),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ROW_GAP),
        )
    }
    if (history.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.wear_tourist_counter_history_empty),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        items(history) { record -> CounterHistoryRow(record) }
        item {
            StandardWearChip(
                label = stringResource(R.string.wear_tourist_counter_clear_history),
                onClick = onClear,
                primary = false,
            )
        }
    }
}

@Composable
private fun CounterTapRow(value: Long, onAdd: (Long) -> Unit) {
    val subtractLabel = stringResource(R.string.wear_tourist_counter_subtract_one)
    val addLabel = stringResource(R.string.wear_tourist_counter_add_one)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { onAdd(-1L) },
            enabled = value > 0L,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier
                .size(MINUS_BUTTON_SIZE)
                .semantics { contentDescription = subtractLabel },
        ) {
            Text(text = "-1", style = MaterialTheme.typography.button)
        }
        Button(
            onClick = { onAdd(1L) },
            modifier = Modifier
                .size(PLUS_BUTTON_SIZE)
                .semantics { contentDescription = addLabel },
        ) {
            Text(text = "+1", style = MaterialTheme.typography.title1, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CounterExtraSteps(onAdd: (Long) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EXTRA_STEPS_INSET),
        horizontalArrangement = Arrangement.spacedBy(STEP_GAP, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(STEP_GAP),
    ) {
        WearTouristCounterState.EXTRA_STEPS.forEach { step ->
            CompactChip(
                onClick = { onAdd(step) },
                label = { Text(text = "+$step") },
            )
        }
    }
}

@Composable
private fun CounterHistoryRow(record: WearTouristCounterRecord) {
    val context = LocalContext.current
    val range = remember(record) {
        DateUtils.formatDateRange(context, record.startedAtMillis, record.endedAtMillis, HISTORY_TIME_FLAGS)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = record.value.toString(),
            style = MaterialTheme.typography.body1,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = range,
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
