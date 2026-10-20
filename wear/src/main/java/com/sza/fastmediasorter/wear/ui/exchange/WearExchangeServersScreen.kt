package com.sza.fastmediasorter.wear.ui.exchange

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import com.sza.fastmediasorter.wear.ui.common.EXCHANGE_DETAILS_ON_PHONE_RES
import com.sza.fastmediasorter.wear.ui.common.StandardWearAlertDialog
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import timber.log.Timber

/** The empty-state line: item 1, under the title item. */
private const val EMPTY_STATE_ITEM = 1

/**
 * S4158: the exchange servers this watch joined from the phone. One chip per server - its address, its login and
 * the "Outgoing" label on the one the watch broadcasts through. A tap offers making it outgoing or removing it;
 * there is no way to add one here, because the watch has no surface to type a server on (strategic 5.3).
 */
@Composable
fun WearExchangeServersScreen(
    onOpenBroadcasts: () -> Unit = {},
    onOpenFolders: (WearExchangeEntry) -> Unit = {},
    viewModel: WearExchangeServersViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    // S4411: the shared opening anchor centres item 2, which on an empty list is the long paragraph after
    // the empty-state line - the screen opened on a wall of text with its own state off the glass. An empty
    // list opens on that line instead; a list with servers keeps the shared rule.
    val listState = rememberWearListState(
        initialCenterItemIndex = if (entries?.isEmpty() == true) EMPTY_STATE_ITEM else WEAR_LIST_ANCHOR,
        positionKey = SettingsRoutes.EXCHANGE_SERVERS,
    )
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()

    when (val shown = overlay) {
        WearExchangeServersOverlay.None -> ServerList(
            entries = entries,
            listState = listState,
            onOpen = viewModel::open,
            onOpenBroadcasts = onOpenBroadcasts,
        )
        is WearExchangeServersOverlay.Actions -> EntryActions(
            entry = shown.entry,
            onMakeOutgoing = { viewModel.makeOutgoing(shown.entry) },
            onOpenFolders = {
                viewModel.dismiss()
                onOpenFolders(shown.entry)
            },
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
            message = removalDetails(shown.removal),
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
    onOpenBroadcasts: () -> Unit,
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState, centered = true) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_exchange_mark_small),
                        contentDescription = stringResource(R.string.wear_exchange_servers_title),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.wear_exchange_servers_title),
                        style = MaterialTheme.typography.title2,
                        textAlign = TextAlign.Center,
                    )
                }
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
            // S4229: the account's broadcasts of every server, one screen; it needs at least one server to ask.
            if (entries?.isNotEmpty() == true) {
                item(key = "broadcasts") {
                    Chip(
                        onClick = onOpenBroadcasts,
                        label = { Text(text = stringResource(R.string.wear_exchange_broadcasts), maxLines = 1) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
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
            // Contract DEVICE-EXCHANGE 27 item DE: what the server sees, in these words wherever it is described.
            item(key = "honest") {
                Text(
                    text = stringResource(R.string.wear_exchange_server_honest_text),
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EntryActions(
    entry: WearExchangeEntry,
    onMakeOutgoing: () -> Unit,
    onOpenFolders: () -> Unit,
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
        // S4228: the account's folders belong to the entry, so the way to them is the entry's own action.
        Chip(
            onClick = onOpenFolders,
            label = {
                Text(
                    text = stringResource(R.string.exchange_account_folders),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
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

// Contract 25 item DA: a removal that never reached the server says so and points at the phone.
@Composable
private fun removalDetails(removal: WearExchangeRemoval): String? {
    if (removal != WearExchangeRemoval.FORGOTTEN_LOCALLY) return null
    LaunchedEffect(removal) {
        Timber.i("Exchange failure shown on removal: code=%s", WearExchangeMessage.UNREACHABLE_SERVER.code)
    }
    return stringResource(EXCHANGE_DETAILS_ON_PHONE_RES)
}

@StringRes
private fun removalMessageOf(removal: WearExchangeRemoval): Int = when (removal) {
    WearExchangeRemoval.REVOKED -> R.string.wear_exchange_removed_revoked
    WearExchangeRemoval.FORGOTTEN_LOCALLY -> R.string.wear_exchange_removed_locally
}
