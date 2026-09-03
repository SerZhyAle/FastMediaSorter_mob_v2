package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.netmonitor.WearGnssDetails
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.netmonitor.WearSatelliteInfo
import com.sza.fastmediasorter.wear.domain.netmonitor.WearTrafficRate
import com.sza.fastmediasorter.wear.domain.netmonitor.WearWifiDetails
import com.sza.fastmediasorter.wear.domain.netmonitor.formatRate
import com.sza.fastmediasorter.wear.domain.netmonitor.formatTrafficTotal
import com.sza.fastmediasorter.wear.domain.netmonitor.signalFraction
import com.sza.fastmediasorter.wear.ui.common.WearInformationRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import java.util.Date
import java.util.Locale

private val TITLE_BOTTOM_PADDING = 6.dp
private val ROW_SPACING = 4.dp
private val SECTION_ITEM_SPACING = 4.dp
private val TREND_HEIGHT = 32.dp
private val TREND_BAR_WIDTH = 3.dp
private val TREND_BAR_GAP = 2.dp

/**
 * Everything a section can do, carried as one value.
 *
 * Bundled rather than passed one by one because every section page takes the whole set while using
 * two or three of them, and the alternative grows the page's signature with each action added.
 */
data class NetworkMonitorSectionActions(
    val onRequestPermissions: () -> Unit,
    val onCopyValue: (String, String) -> Unit,
    val onProbeConnection: () -> Unit,
    val onRestartSignalWindow: () -> Unit,
    val onResetTrafficTotals: () -> Unit,
)

/**
 * Renders the detail page for the given section inside a scrollable [WearListColumn].
 */
@Composable
fun NetworkMonitorSectionPage(
    section: WearNetworkSection,
    state: NetworkMonitorUiState,
    canRequestPermissions: Boolean,
    actions: NetworkMonitorSectionActions,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberWearListState()
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(SECTION_ITEM_SPACING)
        ) {
            item {
                Text(
                    text = stringResource(section.titleRes()),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }

            if (state.permissionsMissing && isPermissionRequiredFor(section)) {
                item {
                    PermissionNotice(
                        canRequest = canRequestPermissions,
                        onRequest = actions.onRequestPermissions
                    )
                }
            }

            sectionContent(section, state, actions)
        }
    }
}

private fun ScalingLazyListScope.sectionContent(
    section: WearNetworkSection,
    state: NetworkMonitorUiState,
    actions: NetworkMonitorSectionActions
) {
    val snapshot = state.snapshot
    when (section) {
        WearNetworkSection.Summary -> item { SummaryFields(snapshot) }
        WearNetworkSection.Wifi -> item {
            WifiFields(
                wifi = snapshot?.wifiDetails,
                signalHistory = state.signalHistory,
                onCopyIp = actions.onCopyValue,
                onRestartSignalWindow = actions.onRestartSignalWindow
            )
        }
        WearNetworkSection.Mobile -> item {
            MobileFields(snapshot, state.capabilities?.hasMobile == true)
        }
        WearNetworkSection.Bluetooth -> item { BluetoothFields(snapshot) }
        WearNetworkSection.Gnss -> gnssSectionContent(snapshot?.gnssDetails, actions.onCopyValue)
        WearNetworkSection.Traffic -> item {
            TrafficFields(
                traffic = snapshot?.trafficRate,
                totals = state.trafficTotals,
                onResetTotals = actions.onResetTrafficTotals
            )
        }
        WearNetworkSection.Internet -> item {
            InternetFields(
                snapshot = snapshot,
                isProbing = state.isProbing,
                probeResult = state.probeResult,
                onProbe = actions.onProbeConnection,
                onCopyIp = actions.onCopyValue
            )
        }
        WearNetworkSection.History -> historySectionContent(state.history)
    }
}

private fun isPermissionRequiredFor(section: WearNetworkSection): Boolean = when (section) {
    WearNetworkSection.Wifi,
    WearNetworkSection.Bluetooth,
    WearNetworkSection.Gnss -> true
    else -> false
}

@Composable
private fun PermissionNotice(canRequest: Boolean, onRequest: () -> Unit) {
    if (!canRequest) {
        NoticeCaption(stringResource(R.string.wear_netmon_permission_missing))
        return
    }
    NoticeCaption(stringResource(R.string.wear_netmon_permission_request))
    Chip(
        onClick = onRequest,
        colors = ChipDefaults.secondaryChipColors(),
        label = {
            Text(
                text = stringResource(R.string.permission_grant_button),
                style = MaterialTheme.typography.button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NoticeCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SummaryFields(snapshot: WearNetworkSnapshot?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelValue(R.string.wear_netmon_field_transport, snapshot?.activeTransport.asLabel())
        LabelValue(R.string.wear_netmon_field_local_ip, snapshot?.localIp)
        LabelValue(R.string.wear_netmon_field_internet_state, snapshot?.hasInternet.asYesNo())
    }
}

@Composable
private fun WifiFields(
    wifi: WearWifiDetails?,
    signalHistory: List<Int>,
    onCopyIp: (String, String) -> Unit,
    onRestartSignalWindow: () -> Unit
) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.wear_netmon_field_wifi_name)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelValue(R.string.wear_netmon_field_wifi_name, wifi?.ssid)
        LabelValue(
            R.string.wear_netmon_field_wifi_signal,
            wifi?.signalDbm?.let { stringResource(R.string.wear_netmon_value_dbm, it) }
        )
        LabelValue(
            R.string.wear_netmon_field_wifi_speed,
            wifi?.linkSpeedMbps?.let { stringResource(R.string.wear_netmon_value_mbps, it) }
        )
        LabelValue(
            R.string.wear_netmon_field_wifi_freq,
            wifi?.frequencyMhz?.let { stringResource(R.string.wear_netmon_value_mhz, it) }
        )
        LabelValue(R.string.wear_netmon_field_wifi_standard, wifi?.wifiStandard)

        SignalTrend(samples = signalHistory, onRestart = onRestartSignalWindow)

        if (!wifi?.ipAddress.isNullOrBlank()) {
            Chip(
                onClick = { onCopyIp(copyLabel, wifi?.ipAddress.orEmpty()) },
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        text = stringResource(R.string.wear_netmon_action_copy_ip) + ": " + wifi?.ipAddress,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!wifi?.visibleNetworks.isNullOrEmpty()) {
            LabelValue(
                R.string.wear_netmon_field_wifi_visible,
                wifi?.visibleNetworks?.joinToString(separator = ", ")
            )
        }
        Button(
            onClick = { NetworkMonitorActions.openWifiSettings(context) },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_open_settings))
        }
    }
}

/**
 * The signal over time, which a single dBm reading cannot show (strategic goal 7).
 *
 * Bars rather than a line: on a round glass a polyline's ends fall into the curvature, and a bar row
 * degrades to something still readable when only two or three samples exist.
 */
@Composable
private fun SignalTrend(samples: List<Int>, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        Text(
            text = stringResource(R.string.wear_netmon_field_signal_trend),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (samples.isEmpty()) {
            Text(
                text = stringResource(R.string.wear_netmon_history_empty),
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TREND_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(TREND_BAR_GAP, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom
            ) {
                samples.forEach { dbm ->
                    Box(
                        modifier = Modifier
                            .width(TREND_BAR_WIDTH)
                            .height(TREND_HEIGHT * signalFraction(dbm))
                            .background(MaterialTheme.colors.primary)
                    )
                }
            }
        }

        Button(
            onClick = onRestart,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_restart_window))
        }
    }
}

@Composable
private fun MobileFields(snapshot: WearNetworkSnapshot?, hasMobileHardware: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasMobileHardware) {
            LabelValue(R.string.wear_netmon_field_operator, snapshot?.mobileOperator)
            LabelValue(R.string.wear_netmon_field_mobile_present, snapshot?.hasMobileData.asYesNo())
        } else {
            Text(
                text = stringResource(R.string.wear_netmon_mobile_unsupported),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        Button(
            onClick = { NetworkMonitorActions.openWirelessSettings(context) },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_open_settings))
        }
    }
}

@Composable
private fun BluetoothFields(snapshot: WearNetworkSnapshot?) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelValue(R.string.wear_netmon_field_bluetooth_state, snapshot?.isBluetoothEnabled.asYesNo())
        Text(
            text = stringResource(R.string.wear_netmon_bluetooth_read_only),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Button(
            onClick = { NetworkMonitorActions.openBluetoothSettings(context) },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_open_settings))
        }
    }
}

private fun ScalingLazyListScope.gnssSectionContent(
    gnss: WearGnssDetails?,
    onCopyIp: (String, String) -> Unit
) {
    item {
        val coordsText = if (gnss?.latitude != null && gnss.longitude != null) {
            String.format(Locale.US, "%.5f, %.5f", gnss.latitude, gnss.longitude)
        } else {
            null
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LabelValue(R.string.wear_netmon_field_coordinates, coordsText)
            if (coordsText != null) {
                Chip(
                    onClick = { onCopyIp("Coordinates", coordsText) },
                    colors = ChipDefaults.secondaryChipColors(),
                    label = {
                        Text(
                            text = stringResource(R.string.wear_netmon_action_copy_ip) + " (" + coordsText + ")",
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            LabelValue(
                R.string.wear_netmon_field_accuracy,
                gnss?.accuracyMeters?.let { stringResource(R.string.wear_netmon_value_meters, it) }
            )
            LabelValue(
                R.string.wear_netmon_field_satellites,
                gnss?.let {
                    stringResource(R.string.wear_netmon_satellites_count, it.satellitesUsed, it.satellitesVisible)
                }
            )
            val fixFormat = DateFormat.getTimeFormat(LocalContext.current)
            LabelValue(
                R.string.wear_netmon_field_fix_time,
                gnss?.fixTimestampMillis?.let { fixFormat.format(Date(it)) }
            )
        }
    }

    if (!gnss?.satellites.isNullOrEmpty()) {
        items(gnss?.satellites.orEmpty()) { sat ->
            SatelliteRow(sat)
        }
    }

    item {
        val context = LocalContext.current
        Button(
            onClick = { NetworkMonitorActions.openLocationSettings(context) },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_open_settings))
        }
    }
}

@Composable
private fun SatelliteRow(sat: WearSatelliteInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${sat.constellationName} #${sat.svid}",
            style = MaterialTheme.typography.caption2,
            color = if (sat.usedInFix) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
        )
        Text(
            text = String.format(Locale.US, "%.1f dB", sat.cn0DbHz),
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TrafficFields(
    traffic: WearTrafficRate?,
    totals: Pair<Long, Long>?,
    onResetTotals: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelValue(
            R.string.wear_netmon_field_rx_rate,
            traffic?.rxBytesPerSec?.let { formatRate(it) }
        )
        LabelValue(
            R.string.wear_netmon_field_tx_rate,
            traffic?.txBytesPerSec?.let { formatRate(it) }
        )
        LabelValue(
            R.string.wear_netmon_field_traffic_rx,
            totals?.first?.let { formatTrafficTotal(it) }
        )
        LabelValue(
            R.string.wear_netmon_field_traffic_tx,
            totals?.second?.let { formatTrafficTotal(it) }
        )
        Button(
            onClick = onResetTotals,
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_netmon_action_reset_counters))
        }
    }
}

@Composable
private fun InternetFields(
    snapshot: WearNetworkSnapshot?,
    isProbing: Boolean,
    probeResult: Boolean?,
    onProbe: () -> Unit,
    onCopyIp: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LabelValue(R.string.wear_netmon_field_internet_state, snapshot?.hasInternet.asYesNo())
        LabelValue(R.string.wear_netmon_field_transport, snapshot?.activeTransport.asLabel())
        if (!snapshot?.localIp.isNullOrBlank()) {
            Chip(
                onClick = { onCopyIp("Local IP", snapshot?.localIp.orEmpty()) },
                colors = ChipDefaults.secondaryChipColors(),
                label = {
                    Text(
                        text = stringResource(R.string.wear_netmon_action_copy_ip) + ": " + snapshot?.localIp,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isProbing) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        } else {
            Button(
                onClick = onProbe,
                colors = ButtonDefaults.primaryButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wear_netmon_action_probe))
            }
        }

        if (probeResult != null) {
            Text(
                text = if (probeResult) {
                    stringResource(R.string.wear_netmon_action_probe_success)
                } else {
                    stringResource(R.string.wear_netmon_action_probe_failure)
                },
                style = MaterialTheme.typography.caption2,
                color = if (probeResult) MaterialTheme.colors.primary else MaterialTheme.colors.error,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun ScalingLazyListScope.historySectionContent(
    history: List<WearNetworkSnapshot>
) {
    if (history.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.wear_netmon_history_empty),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
        return
    }
    items(history.asReversed()) { entry ->
        HistoryRow(entry)
    }
}

@Composable
private fun HistoryRow(entry: WearNetworkSnapshot) {
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = timeFormat.format(Date(entry.recordedAtMillis)),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = entry.activeTransport.asLabel() ?: stringResource(R.string.wear_netmon_unavailable),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Boolean?.asYesNo(): String? = this?.let {
    stringResource(if (it) R.string.wear_netmon_yes else R.string.wear_netmon_no)
}

/** The link's own name, worded - the enum constant is an identifier, not a label for a user. */
@Composable
private fun WearNetworkTransport?.asLabel(): String? = this?.let { stringResource(it.labelRes()) }

@Composable
private fun LabelValue(labelRes: Int, value: String?) = WearInformationRow(
    labelRes = labelRes,
    value = value ?: stringResource(R.string.wear_netmon_unavailable)
)
