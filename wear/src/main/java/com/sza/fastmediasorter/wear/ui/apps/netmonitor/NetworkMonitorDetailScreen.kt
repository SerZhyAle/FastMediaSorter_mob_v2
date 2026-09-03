package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import timber.log.Timber

/**
 * Screen hosting a single section detail view of the Network Monitor.
 *
 * Takes no back callback on purpose: leaving a section is the platform's edge swipe, which the host
 * graph answers by popping this destination (ADR-1). A button of its own would be a second exit next
 * to the gesture whose absence opened this ticket.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NetworkMonitorDetailScreen(
    sectionKey: String,
    modifier: Modifier = Modifier,
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestable = remember { requestablePermissions() }
    val permissionsState = rememberMultiplePermissionsState(permissions = requestable)
    val canRequestPermissions = requestable.isNotEmpty() && !permissionsState.allPermissionsGranted
    val section = WearNetworkSection.fromKey(sectionKey)

    Timber.d("S2156: section destination opened, key=$sectionKey, resolved=${section.key}")

    NetworkMonitorSectionPage(
        section = section,
        state = state,
        canRequestPermissions = canRequestPermissions,
        actions = NetworkMonitorSectionActions(
            onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
            onCopyValue = { label, value -> viewModel.copyToClipboard(label, value) },
            onProbeConnection = { viewModel.probeConnection() },
            onRestartSignalWindow = { viewModel.restartSignalWindow() },
            onResetTrafficTotals = { viewModel.resetTrafficTotals() }
        ),
        modifier = modifier
    )
}
