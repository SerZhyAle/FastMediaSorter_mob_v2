package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.heartRatePermission
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone
import com.sza.fastmediasorter.wear.ui.apps.bodysensor.history.HeartRateTrendChart
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val TITLE_BOTTOM_PADDING = 6.dp
private val VALUE_VERTICAL_PADDING = 4.dp
private val CARD_CORNER_RADIUS = 8.dp
private const val ZONE_BADGE_ALPHA = 0.2f

/** S3112: one point draws no trend, so the chart appears with the second saved measurement. */
private const val MIN_CHART_ENTRIES = 2

/**
 * S3013: Foreground heart-rate reading screen with physiological zone classification
 * and historical reading status.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BodySensorScreen(
    viewModel: BodySensorViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.BODY_SENSOR),
    onHistoryClick: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requested = remember { listOf(heartRatePermission()) }
    val permissionsState = rememberMultiplePermissionsState(
        permissions = requested,
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

            val lastReading = state.lastReading
            if (state.reading is BodySensorReading.Idle && lastReading != null) {
                item { LastReadingCard(lastReading) }
            }

            item { ReadingValue(state.reading) }

            if (state.history.size >= MIN_CHART_ENTRIES) {
                item { HeartRateTrendChart(entries = state.history) }
            }

            state.currentZone?.let { zone ->
                item { ZoneBadge(zone = zone) }
            }

            if (state.canMeasure) {
                item { MeasureChip(onClick = { measureOrAsk(permissionsState, viewModel) }) }
            }
            if (onHistoryClick != null) {
                item { HistoryChip(onClick = onHistoryClick) }
            }
        }
    }
}

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

@Composable
private fun LastReadingCard(last: HeartRateHistoryEntry) {
    val zone = HeartRateZone.classify(last.bpm)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = VALUE_VERTICAL_PADDING)
            .clip(RoundedCornerShape(CARD_CORNER_RADIUS))
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${stringResource(R.string.body_sensor_bpm, last.bpm)}",
            style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface
        )
        Text(
            text = stringResource(zone.labelRes),
            style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
            color = zone.color,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ZoneBadge(zone: HeartRateZone) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(zone.color.copy(alpha = ZONE_BADGE_ALPHA))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(zone.labelRes),
            style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
            color = zone.color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReadingValue(reading: BodySensorReading) {
    val text = readingText(reading)
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.display2,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun HistoryChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.heart_rate_btn_history_analytics)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun readingText(reading: BodySensorReading): String? = when (reading) {
    is BodySensorReading.Idle -> null
    is BodySensorReading.Measuring -> stringResource(R.string.body_sensor_measuring)
    is BodySensorReading.HeartRate -> stringResource(R.string.body_sensor_bpm, reading.beatsPerMinute)
    is BodySensorReading.Unavailable -> stringResource(reasonTextOf(reading.reason))
}

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
