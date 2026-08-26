package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot

private val ROW_SPACING = 4.dp

/** Dispatches to the page that owns the section. Each page reads only its own fields. */
@Composable
fun NetworkMonitorSectionPage(
    section: WearNetworkSection,
    state: NetworkMonitorUiState,
    canRequestPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = state.snapshot
    SectionColumn(titleRes = section.titleRes(), modifier = modifier) {
        if (state.permissionsMissing) {
            PermissionNotice(canRequest = canRequestPermissions, onRequest = onRequestPermissions)
        }
        when (section) {
            WearNetworkSection.Summary -> SummaryFields(snapshot)
            WearNetworkSection.Wifi -> WifiFields(snapshot)
            WearNetworkSection.Mobile -> MobileFields(snapshot)
            WearNetworkSection.Bluetooth -> BluetoothFields(snapshot)
            WearNetworkSection.Gnss -> GnssFields(snapshot)
            WearNetworkSection.Internet -> InternetFields(snapshot)
            WearNetworkSection.History -> HistoryFields(state.history)
        }
    }
}

private fun WearNetworkSection.titleRes(): Int = when (this) {
    WearNetworkSection.Summary -> R.string.wear_netmon_summary
    WearNetworkSection.Wifi -> R.string.wear_netmon_wifi
    WearNetworkSection.Mobile -> R.string.wear_netmon_mobile
    WearNetworkSection.Bluetooth -> R.string.wear_netmon_bluetooth
    WearNetworkSection.Gnss -> R.string.wear_netmon_gnss
    WearNetworkSection.Internet -> R.string.wear_netmon_internet
    WearNetworkSection.History -> R.string.wear_netmon_history
}

/**
 * The notice is the control wherever the platform can still raise the dialog.
 *
 * A caption that names a missing permission and cannot ask for it leaves the program unrecoverable
 * from inside itself, which is what every page read before S2008. Where the API level offers nothing
 * to request, the plain caption stays: a tap that raises no dialog is worse than a sentence.
 */
@Composable
private fun PermissionNotice(canRequest: Boolean, onRequest: () -> Unit) {
    val notice = stringResource(R.string.wear_netmon_permission_missing)
    if (!canRequest) {
        Text(
            text = notice,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        return
    }
    Chip(
        onClick = onRequest,
        colors = ChipDefaults.secondaryChipColors(),
        label = {
            Text(
                text = notice,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SectionColumn(
    titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center
        )
        content()
    }
}

@Composable
private fun SummaryFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_transport, snapshot?.activeTransport?.name)
    LabelValue(R.string.wear_netmon_field_internet_state, snapshot?.hasInternet.asYesNo())
    LabelValue(R.string.wear_netmon_field_wifi_name, snapshot?.wifiNetworkName)
}

@Composable
private fun WifiFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_wifi_name, snapshot?.wifiNetworkName)
    LabelValue(
        R.string.wear_netmon_field_wifi_signal,
        snapshot?.wifiSignalDbm?.let { stringResource(R.string.wear_netmon_value_dbm, it) }
    )
    LabelValue(
        R.string.wear_netmon_field_wifi_speed,
        snapshot?.wifiLinkSpeedMbps?.let { stringResource(R.string.wear_netmon_value_mbps, it) }
    )
    LabelValue(
        R.string.wear_netmon_field_wifi_visible,
        snapshot?.visibleWifiNetworks?.joinToString(separator = ", ")?.takeIf { it.isNotBlank() }
    )
}

@Composable
private fun MobileFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_mobile_present, snapshot?.hasMobileData.asYesNo())
    LabelValue(R.string.wear_netmon_field_operator, snapshot?.mobileOperator)
}

@Composable
private fun BluetoothFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_bluetooth_state, snapshot?.isBluetoothEnabled.asYesNo())
}

@Composable
private fun GnssFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_location_provider, snapshot?.hasLocationProvider.asYesNo())
}

@Composable
private fun InternetFields(snapshot: WearNetworkSnapshot?) {
    LabelValue(R.string.wear_netmon_field_internet_state, snapshot?.hasInternet.asYesNo())
    LabelValue(R.string.wear_netmon_field_transport, snapshot?.activeTransport?.name)
}

/**
 * The readings of this visit only. Nothing is stored: the list dies with the view model, which is
 * what keeps the program from paying for itself after the user leaves.
 */
@Composable
private fun HistoryFields(history: List<WearNetworkSnapshot>) {
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.wear_netmon_history_empty),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        return
    }
    history.asReversed().forEach { entry ->
        LabelValue(
            R.string.wear_netmon_field_transport,
            entry.activeTransport?.name
        )
    }
}

/** A boolean the watch could not answer stays null, so the row says "not measured" rather than "no". */
@Composable
private fun Boolean?.asYesNo(): String? = this?.let {
    stringResource(if (it) R.string.wear_netmon_yes else R.string.wear_netmon_no)
}

@Composable
private fun LabelValue(labelRes: Int, value: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ROW_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = value ?: stringResource(R.string.wear_netmon_unavailable),
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}
