package com.sza.fastmediasorter.wear.ui.exchange

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeRemoval
import com.sza.fastmediasorter.wear.ui.common.StandardWearAlertDialog
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes

/**
 * S4158: the exchange servers this watch joined from the phone. One chip per server - its address, its login and
 * the "Outgoing" label on the one the watch broadcasts through. A tap offers making it outgoing or removing it;
 * there is no way to add one here, because the watch has no surface to type a server on (strategic 5.3).
 */
@Composable
fun WearExchangeServersScreen(
    viewModel: WearExchangeServersViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.EXCHANGE_SERVERS),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()

    when (val shown = overlay) {
        WearExchangeServersOverlay.None -> ServerList(entries, listState, onOpen = viewModel::open)
        is WearExchangeServersOverlay.Actions -> EntryActions(
            entry = shown.entry,
            onMakeOutgoing = { viewModel.makeOutgoing(shown.entry) },
            onRemove = { viewModel.askRemoval(shown.entry) },
            onDismiss = viewModel::dismiss,
        )
        is WearExchangeServersOverlay.ConfirmRemoval -> StandardWearAlertDialog(
            show = true,
            title = stringResource(R.string.wear_exchange_remove_confirm),
            onConfirm = { viewModel.remove(shown.entry) },
            onDismissRequest = viewModel::dismiss,
            confirmLabel = stringResource(R.string.wear_exchange_remove),
            cancelLabel = stringResource(R.string.cancel),
            isDestructive = true,
        )
        WearExchangeServersOverlay.Removing -> CenteredText(stringResource(R.string.wear_exchange_removing))
        is WearExchangeServersOverlay.Removed -> StandardWearAlertDialog(
            show = true,
            title = stringResource(removalMessageOf(shown.removal)),
            onConfirm = viewModel::dismiss,
            onDismissRequest = viewModel::dismiss,
            confirmLabel = stringResource(R.string.ok),
            cancelLabel = null,
        )
    }
}

@Composable
private fun ServerList(
    entries: List<WearExchangeEntry>?,
    listState: ScalingLazyListState,
    onOpen: (WearExchangeEntry) -> Unit,
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState, centered = true) {
            item {
                Text(
                    text = stringResource(R.string.wear_exchange_servers_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            if (entries?.isEmpty() == true) {
                item {
                    Text(
                        text = stringResource(R.string.wear_exchange_servers_empty),
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(entries.orEmpty(), key = { it.identity }) { entry ->
                Chip(
                    onClick = { onOpen(entry) },
                    label = { Text(text = addressOf(entry), maxLines = 1) },
                    secondaryLabel = { Text(text = captionOf(entry), maxLines = 1) },
                    colors = if (entry.outgoing) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EntryActions(
    entry: WearExchangeEntry,
    onMakeOutgoing: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    StandardWearAlertDialog(
        show = true,
        title = addressOf(entry),
        message = captionOf(entry),
        onConfirm = onRemove,
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.wear_exchange_remove),
        cancelLabel = stringResource(R.string.cancel),
        isDestructive = true,
    ) {
        // The outgoing entry has nothing to become, so the action is offered only on the others.
        if (!entry.outgoing) {
            Chip(
                onClick = onMakeOutgoing,
                label = {
                    Text(
                        text = stringResource(R.string.wear_exchange_make_outgoing),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}

// An IPv6 literal is bracketed, as the phone writes the address of an entry.
private fun addressOf(entry: WearExchangeEntry): String {
    val host = if (':' in entry.host) "[${entry.host}]" else entry.host
    return "$host:${entry.port}"
}

@Composable
private fun captionOf(entry: WearExchangeEntry): String {
    val login = stringResource(R.string.wear_exchange_login, entry.login)
    return if (entry.outgoing) "$login · ${stringResource(R.string.wear_exchange_outgoing_label)}" else login
}

@StringRes
private fun removalMessageOf(removal: WearExchangeRemoval): Int = when (removal) {
    WearExchangeRemoval.REVOKED -> R.string.wear_exchange_removed_revoked
    WearExchangeRemoval.FORGOTTEN_LOCALLY -> R.string.wear_exchange_removed_locally
}
