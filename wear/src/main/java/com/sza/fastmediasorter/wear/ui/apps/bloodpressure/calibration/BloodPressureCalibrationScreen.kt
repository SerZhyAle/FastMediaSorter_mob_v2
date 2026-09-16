package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCalibration
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.MeasuringIndicator
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.StatusText
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.captureReasonText
import com.sza.fastmediasorter.wear.ui.apps.bloodpressure.rejectionText
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlin.math.roundToInt

private val SECTION_PADDING = 8.dp
private val DELETE_BUTTON_SIZE = 32.dp

/**
 * S3113: the calibration screen the owner asked for on 2026-09-17 - measure with the cuff a few times, type
 * each reading, and the watch keeps the pairs it will estimate from.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BloodPressureCalibrationScreen(
    viewModel: BloodPressureCalibrationViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.BLOOD_PRESSURE_CALIBRATION)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requested = remember { listOf(heartRatePermission()) }
    val permissionsState = rememberMultiplePermissionsState(
        permissions = requested,
        onPermissionsResult = { viewModel.startRecording() }
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(state = listState, modifier = Modifier.fillMaxSize(), centered = true) {
            item { Title() }
            item { StatusText(stringResource(R.string.blood_pressure_calibration_hint)) }
            state.window?.let { window -> item { WindowStatus(window) } }
            if (state.justSaved) {
                item { StatusText(stringResource(R.string.blood_pressure_calibration_saved)) }
            }
            if (state.canRecord) {
                item { RecordChip(onClick = { recordOrAsk(permissionsState, viewModel) }) }
            }
            item {
                PressureStepperRow(
                    label = stringResource(R.string.blood_pressure_systolic),
                    value = state.systolicInput,
                    onValueChange = viewModel::onSystolicChanged,
                    onAdjust = viewModel::adjustSystolic,
                    descPair = stringResource(R.string.blood_pressure_decrease_systolic) to
                        stringResource(R.string.blood_pressure_increase_systolic),
                    imeAction = ImeAction.Next
                )
            }
            item {
                PressureStepperRow(
                    label = stringResource(R.string.blood_pressure_diastolic),
                    value = state.diastolicInput,
                    onValueChange = viewModel::onDiastolicChanged,
                    onAdjust = viewModel::adjustDiastolic,
                    descPair = stringResource(R.string.blood_pressure_decrease_diastolic) to
                        stringResource(R.string.blood_pressure_increase_diastolic),
                    imeAction = ImeAction.Done
                )
            }
            state.errorMessageRes?.let { res -> item { StatusText(stringResource(res)) } }
            if (state.canSave) {
                item { SaveChip(onClick = viewModel::save) }
            }
            if (state.pairs.isNotEmpty()) {
                item { StatusText(stringResource(R.string.blood_pressure_calibration_pairs_title)) }
                state.pairs.forEach { pair -> item { PairRow(pair, onDelete = { viewModel.delete(pair.id) }) } }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private fun recordOrAsk(permissionsState: MultiplePermissionsState, viewModel: BloodPressureCalibrationViewModel) {
    if (permissionsState.allPermissionsGranted) {
        viewModel.startRecording()
    } else {
        permissionsState.launchMultiplePermissionRequest()
    }
}

@Composable
private fun Title() {
    Text(
        text = stringResource(R.string.blood_pressure_calibration_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WindowStatus(window: CalibrationWindowState) {
    when (window) {
        is CalibrationWindowState.Capturing -> MeasuringIndicator(window.elapsedMillis, window.totalMillis)
        is CalibrationWindowState.Ready -> StatusText(stringResource(R.string.blood_pressure_capture_done))
        is CalibrationWindowState.Rejected -> StatusText(stringResource(rejectionText(window.reason)))
        is CalibrationWindowState.Unavailable -> StatusText(stringResource(captureReasonText(window.reason)))
    }
}

@Composable
private fun RecordChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_capture_start)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PADDING),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun SaveChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_save)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = SECTION_PADDING),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun PairRow(pair: BloodPressureCalibration, onDelete: () -> Unit) {
    val summary = stringResource(
        R.string.blood_pressure_calibration_pair,
        pair.systolic,
        pair.diastolic,
        pair.features.heartRateBpm.roundToInt()
    )
    val date = LocalWearDateTimeFormatter.current.formatDateTime(pair.timestampMillis, LocalWearUnitSystem.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SECTION_PADDING, vertical = 2.dp)
            .clip(RoundedCornerShape(SECTION_PADDING))
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = SECTION_PADDING, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$summary\n$date",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        CompactButton(
            onClick = onDelete,
            modifier = Modifier.size(DELETE_BUTTON_SIZE),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.blood_pressure_calibration_delete, summary)
            )
        }
    }
}
