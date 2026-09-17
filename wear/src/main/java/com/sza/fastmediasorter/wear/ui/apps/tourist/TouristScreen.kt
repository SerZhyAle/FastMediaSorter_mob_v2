package com.sza.fastmediasorter.wear.ui.apps.tourist

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlinx.coroutines.launch
import timber.log.Timber

private val TILE_SPACING = 4.dp

/**
 * S3007 / S3015: Tourist telemetry and athlete navigation dashboard for Wear OS.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TouristScreen(
    onLaunchSos: () -> Unit,
    viewModel: TouristViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.TOURIST),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry = state.telemetry
    val coroutineScope = rememberCoroutineScope()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        onPermissionsResult = {
            viewModel.refreshPermissionState()
        },
    )

    LaunchedEffect(Unit) {
        if (!telemetry.hasLocationPermission && !permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    // The telemetry source binds its location listeners when it is subscribed, so a permission granted
    // afterwards only takes effect once the source is re-subscribed.
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            viewModel.refreshPermissionState()
        }
    }

    // A watch without a temperature sensor drops the metric entirely rather than showing a dash.
    val secondaryMetrics = remember(telemetry.focusedMetric, telemetry.hasBodyTemperatureSensor) {
        TouristMetricType.values().filter { metric ->
            metric != telemetry.focusedMetric &&
                (metric != TouristMetricType.BODY_TEMPERATURE || telemetry.hasBodyTemperatureSensor)
        }
    }

    Timber.d("S3227: tourist screen location permission=${telemetry.hasLocationPermission}")

    val promoteMetric: (TouristMetricType) -> Unit = { metricType ->
        viewModel.selectMetric(metricType)
        // Without the jump the list stays where it was and nothing shows that the main panel changed.
        coroutineScope.launch { listState.animateScrollToItem(0) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isAthleteMode) {
            TouristAthleteCard(
                state = telemetry,
                isMetric = state.isMetricSystem,
                isScreenLocked = state.isScreenLocked,
                onSelectMetric = { viewModel.selectMetric(it) },
                onLockScreen = { viewModel.setScreenLocked(true) },
            )
        } else {
            TouristDashboardContent(
                telemetry = telemetry,
                isMetricSystem = state.isMetricSystem,
                isScreenLocked = state.isScreenLocked,
                secondaryMetrics = secondaryMetrics,
                listState = listState,
                onRequestLocationPermission = { permissionsState.launchMultiplePermissionRequest() },
                onSelectMetric = promoteMetric,
                onResetTrip = { viewModel.resetTrip() },
                onResetSteps = { viewModel.resetSteps() },
                onToggleAthleteMode = { viewModel.toggleAthleteMode() },
                onLockScreen = { viewModel.setScreenLocked(true) },
                onLaunchSos = onLaunchSos,
            )
        }

        if (state.isScreenLocked) {
            TouristLockOverlay(
                onUnlock = { viewModel.setScreenLocked(false) },
            )
        }
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TouristDashboardContent(
    telemetry: WearTouristState,
    isMetricSystem: Boolean,
    isScreenLocked: Boolean,
    secondaryMetrics: List<TouristMetricType>,
    listState: ScalingLazyListState,
    onRequestLocationPermission: () -> Unit,
    onSelectMetric: (TouristMetricType) -> Unit,
    onResetTrip: () -> Unit,
    onResetSteps: () -> Unit,
    onToggleAthleteMode: () -> Unit,
    onLockScreen: () -> Unit,
    onLaunchSos: () -> Unit,
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_tourist_app),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }

            if (!telemetry.hasLocationPermission) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompactChip(
                            onClick = onRequestLocationPermission,
                            label = { Text(stringResource(R.string.wear_tourist_permission_grant)) },
                            colors = ChipDefaults.primaryChipColors(),
                        )
                    }
                }
            }

            item {
                TouristHeroCard(
                    state = telemetry,
                    isMetric = isMetricSystem,
                    isScreenLocked = isScreenLocked,
                    onLockScreen = onLockScreen,
                    modifier = Modifier.padding(horizontal = TILE_SPACING),
                )
            }

            item {
                if (isScreenLocked) {
                    TouristUnlockHint(modifier = Modifier.padding(vertical = TILE_SPACING))
                } else {
                    TouristActionsRow(
                        onResetTrip = onResetTrip,
                        onResetSteps = onResetSteps,
                        onToggleAthleteMode = onToggleAthleteMode,
                        onLaunchSos = {
                            onLaunchSos()
                        },
                        modifier = Modifier.padding(vertical = TILE_SPACING),
                    )
                }
            }

            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TILE_SPACING),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
                ) {
                    secondaryMetrics.forEach { metricType ->
                        TouristSecondaryCard(
                            metricType = metricType,
                            state = telemetry,
                            isMetric = isMetricSystem,
                            onClick = { onSelectMetric(metricType) },
                        )
                    }
                }
            }
        }
    }
}
