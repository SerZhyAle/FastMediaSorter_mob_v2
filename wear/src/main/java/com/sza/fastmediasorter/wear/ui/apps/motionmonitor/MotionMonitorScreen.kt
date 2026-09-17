package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import com.sza.fastmediasorter.wear.ui.common.WearInformationRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 8.dp
private val GROUP_TOP_PADDING = 10.dp
private val CAPTION_TOP_PADDING = 2.dp

/**
 * Live motion and activity readings, for exactly as long as this screen is open.
 *
 * Every stream states its own case: a reading when it is delivering, and one of three distinct sentences
 * when it is not - no such sensor, permission refused, or absent from this edition (S2458 §5.4). None of
 * them is rendered as a zero, because zero is a legitimate accelerometer value.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MotionMonitorScreen(
    viewModel: MotionMonitorViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.MOTION_MONITOR),
    onHistoryClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestable = remember { requestableActivityPermissions() }
    val permissionsState = rememberMultiplePermissionsState(permissions = requestable)

    LaunchedEffect(state.snapshotSaved) {
        if (state.snapshotSaved) {
            Toast.makeText(context, R.string.motion_action_snapshot_saved, Toast.LENGTH_SHORT).show()
        }
    }

    // S3111: the one screen after the calculator and the game to pin an opaque container. It is a dense
    // grid of small figures read at a glance, and a moving or photographic backdrop competes with every
    // one of them; black also keeps the captions' contrast independent of the chosen wallpaper.
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
        background = Color.Black
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(CAPTION_TOP_PADDING)
        ) {
            item { ScreenTitle() }

            item { GroupTitle(R.string.wear_motion_monitor_group_activity) }
            items(state.activity) { row -> StreamRow(row) }

            // S3111: the grant sits inside the group whose rows read "Activity access not granted",
            // not at the end of the screen behind four unrelated actions - the refusal and its cure
            // have to be readable in one glance.
            if (state.canRequestPermission && requestable.isNotEmpty()) {
                item {
                    GrantChip(onClick = { permissionsState.launchMultiplePermissionRequest() })
                }
            }

            item { GroupTitle(R.string.wear_motion_monitor_group_motion) }
            items(state.motion) { row -> StreamRow(row) }

            item {
                ActionChip(
                    labelRes = R.string.motion_action_reset_steps,
                    onClick = { viewModel.resetSteps() }
                )
            }

            item {
                ActionChip(
                    labelRes = R.string.motion_action_save_snapshot,
                    onClick = { viewModel.saveCurrentSnapshot() }
                )
            }

            item {
                ActionChip(
                    labelRes = R.string.motion_action_open_map,
                    onClick = { openMap(context) }
                )
            }

            if (onHistoryClick != null) {
                item {
                    ActionChip(
                        labelRes = R.string.motion_btn_history_analytics,
                        onClick = onHistoryClick
                    )
                }
            }
        }
    }
}

private fun openMap(context: Context) {
    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
    try {
        context.startActivity(mapIntent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "No map application available to handle geo intent")
        Toast.makeText(context, R.string.motion_action_map_not_found, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Empty below API 29, where ACTIVITY_RECOGNITION is not a runtime permission and the platform has no
 * dialog to show - asking there would be a button that cannot do anything.
 */
internal fun requestableActivityPermissions(): List<String> = if (
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
) {
    listOf(Manifest.permission.ACTIVITY_RECOGNITION)
} else {
    emptyList()
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.wear_motion_monitor_app),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun GroupTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GROUP_TOP_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StreamRow(row: MotionStreamRow) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WearInformationRow(labelRes = labelOf(row.id), value = readingOf(row))
        if (row.availability == WearSensorAvailability.Available) {
            DeliveryCaption(row)
        }
    }
}

/**
 * The three delivery figures, or the sentence that no event has arrived yet.
 *
 * The distinction matters more than it looks: a registered stream showing "0 ev" and a stream that never
 * registered would otherwise read identically on a screen the user glances at.
 */
@Composable
private fun DeliveryCaption(row: MotionStreamRow) {
    val ageMillis = row.ageMillis
    val caption = if (ageMillis == null) {
        stringResource(R.string.wear_motion_monitor_waiting)
    } else {
        stringResource(
            R.string.wear_motion_monitor_delivery,
            row.eventCount,
            formatHertz(row.hertz),
            formatAgeSeconds(ageMillis)
        )
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CAPTION_TOP_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun GrantChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.wear_motion_monitor_grant)) },
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * S3111: no `fillMaxWidth`. A button is as wide as its own label and no wider, and the list's own
 * `horizontalAlignment` - `CenterHorizontally` on `ScalingLazyColumn` - is what centres it on the watch.
 */
@Composable
private fun ActionChip(
    @StringRes labelRes: Int,
    onClick: () -> Unit
) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun readingOf(row: MotionStreamRow): String = when (row.availability) {
    WearSensorAvailability.NoHardware -> stringResource(R.string.wear_motion_monitor_no_hardware)
    WearSensorAvailability.PermissionDenied -> stringResource(R.string.wear_motion_monitor_permission_denied)
    WearSensorAvailability.NotInThisEdition -> stringResource(R.string.wear_motion_monitor_not_in_edition)
    WearSensorAvailability.Available -> availableReadingOf(row)
}

private fun availableReadingOf(row: MotionStreamRow): String = when (row.id) {
    // The detector fires once per step and its value is a constant 1, so the count IS the reading.
    WearSensorStreamId.STEP_DETECTOR -> row.eventCount.toString()
    WearSensorStreamId.STEP_COUNTER -> row.displayedSteps?.toString() ?: formatStepCount(row.values)
    else -> formatAxes(row.values)
}

@StringRes
private fun labelOf(id: WearSensorStreamId): Int = when (id) {
    WearSensorStreamId.ACCELEROMETER -> R.string.wear_motion_monitor_accelerometer
    WearSensorStreamId.GYROSCOPE -> R.string.wear_motion_monitor_gyroscope
    WearSensorStreamId.ROTATION_VECTOR -> R.string.wear_motion_monitor_rotation_vector
    WearSensorStreamId.STEP_COUNTER -> R.string.wear_motion_monitor_step_counter
    WearSensorStreamId.STEP_DETECTOR -> R.string.wear_motion_monitor_step_detector
}
