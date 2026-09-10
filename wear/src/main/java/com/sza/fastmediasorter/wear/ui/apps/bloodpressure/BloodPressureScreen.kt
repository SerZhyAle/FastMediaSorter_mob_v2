package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

private val TITLE_BOTTOM_PADDING = 8.dp
private val FIELD_VERTICAL_PADDING = 4.dp

/**
 * S2809: the blood pressure input screen.
 *
 * Shows two number-entry fields (systolic, diastolic), the last saved reading as a hint, a save
 * chip, and a history chip. All interactive elements support D-pad/crown navigation.
 */
@Composable
fun BloodPressureScreen(
    viewModel: BloodPressureViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(),
    onHistoryClick: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            state.lastReading?.let { reading ->
                item { LastReadingText(reading.systolic, reading.diastolic) }
            }
            item {
                SystolicField(
                    value = state.systolicInput,
                    onValueChange = viewModel::onSystolicChanged
                )
            }
            item {
                DiastolicField(
                    value = state.diastolicInput,
                    onValueChange = viewModel::onDiastolicChanged
                )
            }
            state.errorMessageRes?.let { res ->
                item { ErrorText(res) }
            }
            if (state.canSave) {
                item { SaveChip(onClick = { viewModel.save() }) }
            }
            if (onHistoryClick != null) {
                item { HistoryChip(onClick = onHistoryClick) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.blood_pressure_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun LastReadingText(systolic: Int, diastolic: Int) {
    Text(
        text = stringResource(R.string.blood_pressure_last_reading, systolic, diastolic),
        style = MaterialTheme.typography.body2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FIELD_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SystolicField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.blood_pressure_systolic),
        style = MaterialTheme.typography.body2
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        singleLine = true
    )
}

@Composable
private fun DiastolicField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.blood_pressure_diastolic),
        style = MaterialTheme.typography.body2
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        singleLine = true
    )
}

@Composable
private fun ErrorText(stringRes: Int) {
    Text(
        text = stringResource(stringRes),
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FIELD_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SaveChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_save)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun HistoryChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_history_title)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}
