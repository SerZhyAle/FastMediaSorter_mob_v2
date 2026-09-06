package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState

private val TITLE_BOTTOM_PADDING = 8.dp
private val VALUE_VERTICAL_PADDING = 6.dp

/**
 * One foreground heart-rate reading, or the sentence that says why there cannot be one.
 *
 * The permission is asked for on the action and never on entry (S2457 §5). Opening a diagnostic is not
 * consent to be measured, and a screen that throws a health-permission dialog at whoever merely looked
 * at it teaches the user to decline before reading.
 *
 * Nothing here decides whether a refusal is worth retrying - [BodySensorUiState.canMeasure] carries that
 * answer, so this file holds no second `when` over the reasons that could drift from the first.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BodySensorScreen(
    viewModel: BodySensorViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requested = remember { listOf(heartRatePermission()) }
    val permissionsState = rememberMultiplePermissionsState(
        permissions = requested,
        // The availability that decided this screen was read before the user answered the dialog, so it
        // is read again the moment the dialog closes - either way, since a denial is also a new answer.
        onPermissionsResult = { viewModel.refreshAvailability() }
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
            item { ReadingValue(state.reading) }
            if (state.canMeasure) {
                item { MeasureChip(onClick = { measureOrAsk(permissionsState, viewModel) }) }
            }
        }
    }
}

/**
 * The action does one of two things and the user pressed one button for both: with the permission in
 * hand it measures, without it it asks. Asking first is what makes the dialog a consequence of the tap.
 */
@OptIn(ExperimentalPermissionsApi::class)
private fun measureOrAsk(
    permissionsState: MultiplePermissionsState,
    viewModel: BodySensorViewModel
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
        text = stringResource(R.string.body_sensor_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

/**
 * Idle draws nothing at all: before the first measurement there is no reading to show and no refusal to
 * explain, and a placeholder there would be the screen inventing a state the domain does not have.
 */
@Composable
private fun ReadingValue(reading: BodySensorReading) {
    val text = readingText(reading)
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.title3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = VALUE_VERTICAL_PADDING),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MeasureChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.body_sensor_measure)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun readingText(reading: BodySensorReading): String? = when (reading) {
    is BodySensorReading.Idle -> null
    is BodySensorReading.Measuring -> stringResource(R.string.body_sensor_measuring)
    is BodySensorReading.HeartRate -> stringResource(R.string.body_sensor_bpm, reading.beatsPerMinute)
    is BodySensorReading.Unavailable -> stringResource(reasonTextOf(reading.reason))
}

/**
 * Exhaustive with no else branch: S2457 §11 criterion 1 is satisfied by these being seven separate
 * sentences, so an eighth reason must fail compilation here rather than inherit one of them.
 */
@StringRes
private fun reasonTextOf(reason: BodySensorUnavailableReason): Int = when (reason) {
    BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD -> R.string.body_sensor_reason_not_offered_in_this_build
    BodySensorUnavailableReason.PLATFORM_TOO_OLD -> R.string.body_sensor_reason_platform_too_old
    BodySensorUnavailableReason.NO_HARDWARE -> R.string.body_sensor_reason_no_hardware
    BodySensorUnavailableReason.PERMISSION_DENIED -> R.string.body_sensor_reason_permission_denied
    BodySensorUnavailableReason.SENSOR_OFF_BODY -> R.string.body_sensor_reason_sensor_off_body
    BodySensorUnavailableReason.MEASUREMENT_TIMED_OUT -> R.string.body_sensor_reason_measurement_timed_out
    BodySensorUnavailableReason.MEASUREMENT_FAILED -> R.string.body_sensor_reason_measurement_failed
}
