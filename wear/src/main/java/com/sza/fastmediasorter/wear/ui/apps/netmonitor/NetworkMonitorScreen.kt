package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import timber.log.Timber

private val PAGE_PADDING = 8.dp

/**
 * Top-level entry point for the Wear Network Monitor.
 *
 * When [sectionKey] is null or "summary", renders the [NetworkMonitorSummaryScreen] dashboard.
 * When [sectionKey] names a section (e.g. "wifi", "mobile", "gnss"), renders that section's detail view.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NetworkMonitorScreen(
    sectionKey: String? = null,
    onNavigateToSection: (String) -> Unit = {},
    viewModel: NetworkMonitorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestable = remember { requestablePermissions() }
    val permissionsState = rememberMultiplePermissionsState(permissions = requestable)
    val canRequestPermissions = requestable.isNotEmpty() && !permissionsState.allPermissionsGranted

    val targetSection = if (!sectionKey.isNullOrBlank()) {
        WearNetworkSection.fromKey(sectionKey)
    } else {
        WearNetworkSection.Summary
    }

    Timber.d("S2156: monitor entered, section=${targetSection.key}, sections=${state.sections.size}")

    if (targetSection == WearNetworkSection.Summary) {
        if (state.sections.isEmpty()) {
            WearScreenScaffold {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.wear_netmon_unavailable),
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(PAGE_PADDING)
                    )
                }
            }
        } else {
            NetworkMonitorSummaryScreen(
                viewModel = viewModel,
                onNavigateToSection = onNavigateToSection
            )
        }
    } else {
        NetworkMonitorSectionPage(
            section = targetSection,
            state = state,
            canRequestPermissions = canRequestPermissions,
            actions = NetworkMonitorSectionActions(
                onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() },
                onCopyValue = { label, value -> viewModel.copyToClipboard(label, value) },
                onProbeConnection = { viewModel.probeConnection() },
                onRestartSignalWindow = { viewModel.restartSignalWindow() },
                onResetTrafficTotals = { viewModel.resetTrafficTotals() }
            )
        )
    }
}

/**
 * The permissions the sampling reads and that the platform can still be asked for here.
 *
 * Both are declared in the manifest and read by the repository (S2008).
 */
internal fun requestablePermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}
