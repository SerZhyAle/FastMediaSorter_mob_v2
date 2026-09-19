package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.ui.common.StandardWearCard
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val TITLE_BOTTOM_PADDING = 6.dp
private val SECTION_PADDING = 8.dp
private val CARD_VERTICAL_INSET = 4.dp

/**
 * S3012/S3113: the blood-pressure screen - an estimate from the pulse wave, started by opening the screen.
 *
 * Until S3113 this was a manual entry form; the form now lives on the calibration screen, where a typed
 * value is a cuff reading taken together with the pulse wave, and this screen only estimates. The heart-rate
 * permission the capture needs is asked from the "measure again" chip, and the answer restarts the window.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BloodPressureScreen(
    viewModel: BloodPressureViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.BLOOD_PRESSURE),
    onCalibrationClick: (() -> Unit)? = null,
    onHistoryClick: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requested = remember { listOf(heartRatePermission()) }
    val permissionsState = rememberMultiplePermissionsState(
        permissions = requested,
        onPermissionsResult = { viewModel.startMeasurement() }
    )

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
            item { BloodPressureEstimateContent(state.phase) }

            // S3113: the previous value is hidden while a window runs - beside the progress it read as the
            // result of the measurement still in progress.
            val lastReading = state.lastReading
            val showsLastReading = state.phase !is BloodPressureEstimatePhase.Estimated &&
                state.phase !is BloodPressureEstimatePhase.Capturing
            if (showsLastReading && lastReading != null) {
                item { LastReadingCard(lastReading) }
            }
            if (state.canMeasure) {
                item { MeasureChip(onClick = { measureOrAsk(permissionsState, viewModel) }) }
            }
            if (onCalibrationClick != null) {
                item { NavigationChip(R.string.blood_pressure_calibration_title, onCalibrationClick) }
            }
            if (onHistoryClick != null) {
                item { NavigationChip(R.string.blood_pressure_btn_history_analytics, onHistoryClick) }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun measureOrAsk(
    permissionsState: MultiplePermissionsState,
    viewModel: BloodPressureViewModel
) {
    if (permissionsState.allPermissionsGranted) {
        viewModel.startMeasurement()
    } else {
        permissionsState.launchMultiplePermissionRequest()
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
private fun LastReadingCard(reading: BloodPressureHistoryEntry) {
    StandardWearCard(
        modifier = Modifier.padding(horizontal = SECTION_PADDING, vertical = CARD_VERTICAL_INSET)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.blood_pressure_last_reading, reading.systolic, reading.diastolic),
                style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colors.onSurface
            )
            reading.pulse?.let { pulse ->
                Text(
                    text = stringResource(R.string.blood_pressure_pulse, pulse),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface
                )
            }
            Text(
                text = stringResource(sourceText(reading.source)),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MeasureChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_measure_again)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PADDING),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun NavigationChip(label: Int, onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PADDING),
        colors = ChipDefaults.secondaryChipColors()
    )
}
