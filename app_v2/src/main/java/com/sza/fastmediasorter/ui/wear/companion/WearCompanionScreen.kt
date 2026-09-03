package com.sza.fastmediasorter.ui.wear.companion

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel

internal val SPACING_TINY = 4.dp
internal val SPACING_SMALL = 8.dp
internal val SPACING_CARD = 12.dp
internal val SPACING_SECTION = 16.dp

/**
 * S2000: the companion window's frame - an action area that is always visible, then the groups.
 *
 * The frame holds no setting of its own. Every setting belongs to a group, and a group is one call
 * here plus one file, which is what makes "add the next watch setting" an addition rather than a
 * rebuild (strategic §2.4, §5.1 pillar A).
 *
 * The actions stay outside every group on purpose: a push button hidden inside a collapsed group
 * means edits silently never leave the phone (strategic §3.3.4). Each group owns its own expansion
 * state and starts collapsed, so opening the window shows headings rather than one group's contents
 * pushing the rest off the screen (strategic §3.3.3).
 *
 * S2091: every control below carries a `testTag`, which reaches `uiautomator` as a `resource-id` only
 * because `FastMediaSorterComposeTheme` sets `testTagsAsResourceId` for every island (S2096). Do not
 * repeat that flag here - one owner keeps a removal visible instead of silently sparing this screen.
 */
@Composable
fun WearCompanionScreen(
    viewModel: WearSyncViewModel,
    onPushClick: () -> Unit,
    showResourceSelection: Boolean,
    onSelectResourcesClick: () -> Unit,
    onWatchResourceClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val watchSettings by viewModel.watchSettingsState.collectAsState()
    val pendingWatchSources by viewModel.pendingWatchSources.collectAsState()
    val watchPlaybackState by viewModel.watchPlaybackState.collectAsState()

    var watchSettingsExpanded by remember { mutableStateOf(false) }

    // The content is taller than the window on a short phone, and before this the slideshow slider
    // and the push button were the parts that fell past the fold (S1730).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SPACING_SECTION)
            .navigationBarsPadding()
    ) {
        WearCompanionHeader(lastSync = viewModel.lastSyncTimestamp)

        Button(
            onClick = onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wearPushToWatch")
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }

        if (showResourceSelection) {
            Spacer(Modifier.height(SPACING_SMALL))
            OutlinedButton(
                onClick = onSelectResourcesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wearSelectResources")
            ) {
                Text(stringResource(R.string.wear_resource_selection_title))
            }

            // S2034: the watch's own storage as a resource, added on the first tap and opened on
            // every later one - the label says both because the button is one entry point, not two.
            Spacer(Modifier.height(SPACING_SMALL))
            OutlinedButton(
                onClick = onWatchResourceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wearWatchResource")
            ) {
                Text(stringResource(R.string.wear_companion_add_open_resource))
            }
        }

        pendingWatchSources?.let { pending ->
            Spacer(Modifier.height(SPACING_CARD))
            PendingImportCard(
                pending = pending,
                onAccept = { viewModel.acceptWatchImport() },
                onDismiss = { viewModel.dismissWatchImport() }
            )
        }

        watchPlaybackState?.let { playing ->
            Spacer(Modifier.height(SPACING_CARD))
            NowPlayingCard(playing = playing, onCommand = viewModel::sendPlaybackCommand)
        }

        Spacer(Modifier.height(SPACING_SECTION))

        WearWatchSettingsGroup(
            viewModel = viewModel,
            watchSettings = watchSettings,
            pushEnabled = state !is WearSyncUiState.Sending,
            expanded = watchSettingsExpanded,
            onExpandedChange = { watchSettingsExpanded = it }
        )
    }
}

@Composable
private fun WearCompanionHeader(lastSync: Long) {
    Text(
        text = stringResource(R.string.wear_companion),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(Modifier.height(SPACING_SMALL))
    Text(
        text = stringResource(R.string.wear_sync_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(SPACING_SECTION))

    if (lastSync > 0L) {
        Text(
            text = stringResource(
                R.string.wear_last_synced,
                DateUtils.getRelativeTimeSpanString(lastSync)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(SPACING_SMALL))
    }
}

/** S0111 Phase 03: sources the watch offered to hand back, waiting for the owner to accept them. */
@Composable
private fun PendingImportCard(
    pending: WearSourcesExportPayload,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SPACING_CARD)) {
            Text(
                text = stringResource(R.string.wear_import_pending_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(SPACING_TINY))
            Text(
                text = stringResource(
                    R.string.wear_import_pending_desc,
                    pending.sources.size,
                    pending.watchName
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(SPACING_SMALL))
            Row {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wearImportAccept")
                ) {
                    Text(stringResource(R.string.wear_import_accept))
                }
                Spacer(Modifier.padding(horizontal = SPACING_TINY))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wearImportDismiss")
                ) {
                    Text(stringResource(R.string.wear_import_dismiss))
                }
            }
        }
    }
}

/** S0111 Phase 05: what the watch is playing right now, with transport controls back to it. */
@Composable
private fun NowPlayingCard(
    playing: WearPlaybackStatePayload,
    onCommand: (WearPlaybackCommand) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SPACING_CARD)) {
            Text(
                text = stringResource(R.string.wear_now_playing_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(SPACING_TINY))
            Text(text = playing.fileName, style = MaterialTheme.typography.bodySmall)
            if (playing.durationMs > 0) {
                Spacer(Modifier.height(SPACING_TINY))
                LinearProgressIndicator(
                    progress = { (playing.positionMs.toFloat() / playing.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(SPACING_SMALL))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { onCommand(WearPlaybackCommand.PREVIOUS) },
                    modifier = Modifier.testTag("wearPlaybackPrevious")
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.wear_playback_previous)
                    )
                }
                IconButton(
                    onClick = { onCommand(WearPlaybackCommand.PLAY_PAUSE) },
                    modifier = Modifier.testTag("wearPlaybackPlayPause")
                ) {
                    Icon(
                        if (playing.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.wear_playback_play_pause)
                    )
                }
                IconButton(
                    onClick = { onCommand(WearPlaybackCommand.NEXT) },
                    modifier = Modifier.testTag("wearPlaybackNext")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.wear_playback_next)
                    )
                }
            }
        }
    }
}
