package com.sza.fastmediasorter.wear.ui.apps.netmonitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSection
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkTransport
import com.sza.fastmediasorter.wear.domain.netmonitor.formatRate
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

private val TITLE_BOTTOM_PADDING = 6.dp
private val ROW_SPACING = 4.dp
private val CARD_PADDING = 4.dp
private val CARD_LINE_SPACING = 2.dp

/**
 * Root Dashboard screen of the Wear Network Monitor.
 *
 * Renders the top summary card (active connection, local IP, external IP / reachability)
 * and a scrollable list of section Chips showing section name and live status fact.
 */
@Composable
fun NetworkMonitorSummaryScreen(
    viewModel: NetworkMonitorViewModel,
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberScalingLazyListState()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        ScalingLazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
            scalingParams = WearGridScalingParams
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_netmon_summary),
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }

            item {
                SummaryHeaderCard(
                    snapshot = snapshot,
                    externalIp = state.externalIp,
                    onCopyIp = { label, ip -> viewModel.copyToClipboard(label, ip) }
                )
            }

            items(state.sections.filter { it != WearNetworkSection.Summary }) { section ->
                SectionButton(
                    section = section,
                    fact = state.sectionFacts[section] ?: WearSectionFact.None,
                    onClick = { onNavigateToSection(section.key) }
                )
            }
        }
    }
}

/**
 * The phone summary's three lines in a watch-sized card: the active link, then the local and the
 * external address, in that order (strategic section 6, owner decision 6).
 */
@Composable
private fun SummaryHeaderCard(
    snapshot: WearNetworkSnapshot?,
    externalIp: String?,
    onCopyIp: (String, String) -> Unit
) {
    val localIpLabel = stringResource(R.string.wear_netmon_field_local_ip)
    Card(
        onClick = {
            snapshot?.localIp?.let { onCopyIp(localIpLabel, it) }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CARD_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CARD_LINE_SPACING)
        ) {
            Text(
                text = snapshot?.activeTransport?.let { stringResource(it.labelRes()) }
                    ?: stringResource(R.string.wear_netmon_unavailable),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )

            SummaryAddressLine(
                labelRes = R.string.wear_netmon_field_local_ip,
                value = snapshot?.localIp,
                color = MaterialTheme.colors.onSurface
            )

            SummaryAddressLine(
                labelRes = R.string.wear_netmon_field_external_ip,
                value = externalIp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryAddressLine(labelRes: Int, value: String?, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = stringResource(labelRes) + ": " +
            (value ?: stringResource(R.string.wear_netmon_unavailable)),
        style = MaterialTheme.typography.caption2,
        color = color,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SectionButton(
    section: WearNetworkSection,
    fact: WearSectionFact,
    onClick: () -> Unit
) {
    val factText = fact.render()
    Chip(
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        label = {
            Text(
                text = stringResource(section.titleRes()),
                style = MaterialTheme.typography.body2,
                maxLines = 1
            )
        },
        secondaryLabel = factText?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Where a named fact becomes words. Null means the button carries its name alone. */
@Composable
internal fun WearSectionFact.render(): String? = when (this) {
    is WearSectionFact.None -> null
    is WearSectionFact.Literal -> text
    is WearSectionFact.Named -> stringResource(kind.labelRes())
    is WearSectionFact.Satellites -> stringResource(R.string.wear_netmon_fact_satellites, used, visible)
    is WearSectionFact.Rate -> formatRate(bytesPerSec)
    is WearSectionFact.Entries -> stringResource(R.string.wear_netmon_fact_entries, count)
    is WearSectionFact.Signal -> stringResource(R.string.wear_netmon_value_dbm, dbm)
}

private fun WearFactKind.labelRes(): Int = when (this) {
    WearFactKind.On -> R.string.wear_netmon_fact_on
    WearFactKind.Off -> R.string.wear_netmon_fact_off
    WearFactKind.NoModem -> R.string.wear_netmon_fact_no_modem
    WearFactKind.NoSim -> R.string.wear_netmon_fact_no_sim
    WearFactKind.Active -> R.string.wear_netmon_fact_active
    WearFactKind.Ready -> R.string.wear_netmon_fact_ready
    WearFactKind.Reachable -> R.string.wear_netmon_action_probe_success
    WearFactKind.Offline -> R.string.wear_netmon_fact_offline
}

internal fun WearNetworkTransport.labelRes(): Int = when (this) {
    WearNetworkTransport.Wifi -> R.string.wear_netmon_transport_wifi
    WearNetworkTransport.Cellular -> R.string.wear_netmon_transport_cellular
    WearNetworkTransport.Ethernet -> R.string.wear_netmon_transport_ethernet
    WearNetworkTransport.Bluetooth -> R.string.wear_netmon_transport_bluetooth
    WearNetworkTransport.Vpn -> R.string.wear_netmon_transport_vpn
    WearNetworkTransport.Other -> R.string.wear_netmon_transport_other
}

internal fun WearNetworkSection.titleRes(): Int = when (this) {
    WearNetworkSection.Summary -> R.string.wear_netmon_summary
    WearNetworkSection.Wifi -> R.string.wear_netmon_wifi
    WearNetworkSection.Mobile -> R.string.wear_netmon_mobile
    WearNetworkSection.Bluetooth -> R.string.wear_netmon_bluetooth
    WearNetworkSection.Gnss -> R.string.wear_netmon_gnss
    WearNetworkSection.Traffic -> R.string.wear_netmon_traffic
    WearNetworkSection.Internet -> R.string.wear_netmon_internet
    WearNetworkSection.History -> R.string.wear_netmon_history
}
