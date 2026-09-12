package com.sza.fastmediasorter.wear.ui.apps.tourist

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.wear.compose.foundation.lazy.items
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
import timber.log.Timber

/**
 * S3007 / S3015: Tourist telemetry and athlete navigation dashboard for Wear OS.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TouristScreen(
    viewModel: TouristViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry = state.telemetry

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )

    val secondaryMetrics = remember(telemetry.focusedMetric) {
        TouristMetricType.values().filter { it != telemetry.focusedMetric }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isAthleteMode) {
            TouristAthleteCard(
                state = telemetry,
                isMetric = state.isMetricSystem,
                onSelectMetric = { viewModel.selectMetric(it) },
                onLockScreen = { viewModel.setScreenLocked(true) },
                onExitAthleteMode = { viewModel.toggleAthleteMode() },
            )
        } else {
            TouristDashboardContent(
                telemetry = telemetry,
                isMetricSystem = state.isMetricSystem,
                secondaryMetrics = secondaryMetrics,
                listState = listState,
                onRequestLocationPermission = { permissionsState.launchMultiplePermissionRequest() },
                onSelectMetric = { viewModel.selectMetric(it) },
                onResetTrip = { viewModel.resetTrip() },
                onResetSteps = { viewModel.resetSteps() },
                onToggleAthleteMode = { viewModel.toggleAthleteMode() },
                onLockScreen = { viewModel.setScreenLocked(true) },
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
@Composable
private fun TouristDashboardContent(
    telemetry: WearTouristState,
    isMetricSystem: Boolean,
    secondaryMetrics: List<TouristMetricType>,
    listState: ScalingLazyListState,
    onRequestLocationPermission: () -> Unit,
    onSelectMetric: (TouristMetricType) -> Unit,
    onResetTrip: () -> Unit,
    onResetSteps: () -> Unit,
    onToggleAthleteMode: () -> Unit,
    onLockScreen: () -> Unit,
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    CompactChip(
                        onClick = onRequestLocationPermission,
                        label = { Text(stringResource(R.string.wear_tourist_permission_location)) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }

            item {
                TouristHeroCard(
                    state = telemetry,
                    isMetric = isMetricSystem,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item {
                TouristActionsRow(
                    onResetTrip = onResetTrip,
                    onResetSteps = onResetSteps,
                    onToggleAthleteMode = onToggleAthleteMode,
                    onLockScreen = onLockScreen,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            items(secondaryMetrics) { metricType ->
                TouristSecondaryCard(
                    metricType = metricType,
                    state = telemetry,
                    isMetric = isMetricSystem,
                    onClick = { onSelectMetric(metricType) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
