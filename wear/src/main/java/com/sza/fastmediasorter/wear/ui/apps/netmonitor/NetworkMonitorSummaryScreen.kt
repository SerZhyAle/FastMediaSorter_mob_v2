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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
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
import com.sza.fastmediasorter.wear.ui.common.WearInformationRow
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearReportDivider
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 6.dp
private val ROW_SPACING = 4.dp
private val HEADER_LINE_SPACING = 2.dp

/**
 * Root Dashboard screen of the Wear Network Monitor.
 *
 * One report, not a grid of tiles (S2805): the header states the active link and the two addresses,
 * then every section takes a full-width row of its own carrying its name and its live fact. The
 * general view-mode setting is deliberately not read here - at the two and three columns it asks
 * for, a section cell keeps about 54 dp of an inscribed 170 dp square, which truncated the fact
 * away and left a panel that reported nothing.
 */
@Composable
fun NetworkMonitorSummaryScreen(
    viewModel: NetworkMonitorViewModel,
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberWearListState()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
    val nonSummarySections = state.sections.filter { it != WearNetworkSection.Summary }
    Timber.d("S2805: Network Monitor summary as one-column report, sections=%d", nonSummarySections.size)

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
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
                SummaryHeaderBlock(snapshot = snapshot, externalIp = state.externalIp)
            }

            item { WearReportDivider() }

            items(nonSummarySections) { section ->
                SectionRow(
                    section = section,
                    fact = state.sectionFacts[section] ?: WearSectionFact.None,
                    onClick = { onNavigateToSection(section.key) }
                )
            }
        }
    }
}

/**
 * The phone summary's three lines in a watch-sized block: the active link, then the local and the
 * external address, in that order (strategic section 6, owner decision 6).
 *
 * The addresses are ordinary information rows, which already copy their value on a long press
 * (S2775) - the card that used to wrap them carried a tap handler doing the same thing for one of
 * the two, and a block reads as part of the report where a card reads as a control.
 */
@Composable
private fun SummaryHeaderBlock(snapshot: WearNetworkSnapshot?, externalIp: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HEADER_LINE_SPACING)
    ) {
        Text(
            text = snapshot?.activeTransport?.let { stringResource(it.labelRes()) }
                ?: stringResource(R.string.wear_netmon_unavailable),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center
        )

        WearInformationRow(
            labelRes = R.string.wear_netmon_field_local_ip,
            value = snapshot?.localIp ?: stringResource(R.string.wear_netmon_unavailable)
        )

        WearInformationRow(
            labelRes = R.string.wear_netmon_field_external_ip,
            value = externalIp ?: stringResource(R.string.wear_netmon_unavailable)
        )
    }
}

/**
 * One section of the report: its name, its live fact under it, and the whole row opening the
 * section's page.
 *
 * A chip rather than an information row because this row is a control - the chip gives it the
 * interactive height a caption pair does not reach, and states its button role to TalkBack.
 */
@Composable
private fun SectionRow(
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
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = factText?.takeIf { it.isNotEmpty() }?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Where a named fact becomes words. Null means the row carries its name alone. */
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
