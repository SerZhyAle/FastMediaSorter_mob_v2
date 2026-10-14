package com.sza.fastmediasorter.wear.ui.exchange

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcasterGroup
import com.sza.fastmediasorter.wear.domain.model.WearExchangeMessage
import com.sza.fastmediasorter.wear.ui.common.EXCHANGE_DETAILS_ON_PHONE_RES
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.messageRes
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsRoutes
import timber.log.Timber

/**
 * S4229: the broadcasts of the account, grouped by the device that makes them. A tap plays one - sound in the
 * audio player, picture in the video player - and a broadcast the watch cannot play is shown disabled and says so.
 * With one exchange entry the groups stand alone; with several, each entry's address heads its own groups.
 */
@Composable
fun WearAccountBroadcastsScreen(
    navController: NavController,
    viewModel: WearAccountBroadcastsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.EXCHANGE_BROADCASTS),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val unreachable = stringResource(WearExchangeMessage.UNREACHABLE_DEVICE.messageRes()) +
        "\n" + stringResource(EXCHANGE_DETAILS_ON_PHONE_RES)
    val unplayable = stringResource(R.string.wear_exchange_broadcast_unplayable)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WearAccountBroadcastEvent.OpenPlayer -> navController.navigate(
                    if (event.target.isVideo) {
                        WearRoutes.videoPlayer(event.target.fileId)
                    } else {
                        WearRoutes.audioPlayer(event.target.fileId)
                    },
                )
                is WearAccountBroadcastEvent.Unavailable -> Toast.makeText(
                    context,
                    if (event.reason == WearBroadcastPlayback.Reason.UNREACHABLE) unreachable else unplayable,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val fallbackTitle = stringResource(R.string.wear_exchange_broadcast_untitled)
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState, centered = true) {
            item {
                Text(
                    text = stringResource(R.string.wear_exchange_broadcasts),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
            broadcastItems(state, fallbackTitle, viewModel::play)
        }
    }
}

private fun ScalingLazyListScope.broadcastItems(
    state: WearAccountBroadcastsUiState,
    fallbackTitle: String,
    onPlay: (WearAccountBroadcast, String) -> Unit,
) {
    val sections = state.sections
    val showHeaders = sections.size > 1
    if (!state.hasAnswered || sections.isEmpty()) {
        item { StatusLine(R.string.wear_exchange_broadcasts_loading) }
        return
    }
    sections.forEach { section ->
        if (showHeaders) {
            item(key = "entry:${section.entryIdentity}") { SectionHeader(section.entryLabel) }
        }
        when {
            !section.isLoaded && section.failure != null -> item { FailureLines(section.failure) }
            !section.isLoaded && !state.isGraceOver -> item { StatusLine(R.string.wear_exchange_broadcasts_loading) }
            !section.isLoaded -> item { StatusLine(R.string.wear_exchange_broadcasts_failed) }
            section.groups.isEmpty() -> item { StatusLine(R.string.wear_exchange_broadcasts_empty) }
            else -> groupItems(section, fallbackTitle, onPlay)
        }
    }
}

private fun ScalingLazyListScope.groupItems(
    section: WearAccountBroadcastSection,
    fallbackTitle: String,
    onPlay: (WearAccountBroadcast, String) -> Unit,
) {
    section.groups.forEachIndexed { index, group ->
        item(key = "group:${section.entryIdentity}:$index") { GroupHeader(group) }
        group.broadcasts.forEach { broadcast ->
            item(key = "b:${section.entryIdentity}:${broadcast.broadcastId}") {
                BroadcastChip(broadcast, fallbackTitle, onPlay)
            }
        }
    }
}

@Composable
private fun BroadcastChip(
    broadcast: WearAccountBroadcast,
    fallbackTitle: String,
    onPlay: (WearAccountBroadcast, String) -> Unit,
) {
    val kind = stringResource(
        if (broadcast.isVideo) R.string.wear_exchange_broadcast_video else R.string.wear_exchange_broadcast_sound,
    )
    val caption = when {
        !broadcast.isPlayable -> stringResource(R.string.wear_exchange_broadcast_unplayable)
        broadcast.isProtected -> "$kind · ${stringResource(R.string.wear_exchange_broadcast_protected)}"
        else -> kind
    }
    Chip(
        onClick = { onPlay(broadcast, fallbackTitle) },
        enabled = broadcast.isPlayable,
        label = { Text(text = broadcast.title ?: fallbackTitle, maxLines = 1) },
        secondaryLabel = { Text(text = caption, maxLines = 1) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GroupHeader(group: WearBroadcasterGroup) {
    val name = group.deviceName.orEmpty()
    val text = if (group.isOnline) name else "$name · ${stringResource(R.string.wear_exchange_broadcast_offline)}"
    Text(
        text = text,
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.title3,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** Contract 25 item DA on the watch: what happened, in the table's words, then the one fixed line. */
@Composable
private fun FailureLines(failure: WearExchangeMessage) {
    LaunchedEffect(failure) {
        Timber.i("Exchange failure shown on the account list: code=%s", failure.code)
        Timber.d("S4236: account list failure shown, code=%s", failure.code)
    }
    StatusLine(failure.messageRes())
    StatusLine(EXCHANGE_DETAILS_ON_PHONE_RES)
}

@Composable
private fun StatusLine(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.body2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        textAlign = TextAlign.Center,
    )
}
