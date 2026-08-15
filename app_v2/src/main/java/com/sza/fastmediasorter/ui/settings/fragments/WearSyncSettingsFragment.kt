package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.helpers.BeamAnimationDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearSyncSettingsFragment : BottomSheetDialogFragment() {

    private val viewModel: WearSyncViewModel by viewModels()

    // The island reads its colours off its own context, so it must be the dialog's themed context
    // (DialogFragment hands it out through the inflater) rather than the plain activity context -
    // otherwise the sheet chrome and the content inside it can resolve different surfaces.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                FastMediaSorterComposeTheme {
                    WearSyncScreen(
                        viewModel = viewModel,
                        onPushClick = { launchBeamDialog() }
                    )
                }
            }
        }

    private fun launchBeamDialog() {
        viewModel.startPush()
        if (childFragmentManager.findFragmentByTag("beam_dialog") == null) {
            BeamAnimationDialog().show(childFragmentManager, "beam_dialog")
        }
    }
}

@Composable
private fun WearSyncScreen(
    viewModel: WearSyncViewModel,
    onPushClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lastSync = viewModel.lastSyncTimestamp
    val watchSettings by viewModel.watchSettingsState.collectAsState()
    val pendingWatchSources by viewModel.pendingWatchSources.collectAsState()
    val watchPlaybackState by viewModel.watchPlaybackState.collectAsState()
    var settingsExpanded by remember { mutableStateOf(false) }

    // Local mutable settings state, initialized from viewModel or defaults
    var audioEnabled by remember(watchSettings) { mutableStateOf(watchSettings?.audioEnabled ?: true) }
    var videoEnabled by remember(watchSettings) { mutableStateOf(watchSettings?.videoEnabled ?: true) }
    var imagesEnabled by remember(watchSettings) { mutableStateOf(watchSettings?.imagesEnabled ?: true) }
    var slideshowEnabled by remember(watchSettings) { mutableStateOf(watchSettings?.slideshowEnabled ?: false) }
    var albumArtEnabled by remember(watchSettings) { mutableStateOf(watchSettings?.downloadAlbumArt ?: true) }
    var slideshowInterval by remember(watchSettings) { mutableStateOf((watchSettings?.slideshowIntervalSeconds ?: 5).toFloat()) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
        Text(
            text = stringResource(R.string.wear_companion),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.wear_sync_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (lastSync > 0L) {
            Text(
                text = stringResource(
                    R.string.wear_last_synced,
                    DateUtils.getRelativeTimeSpanString(lastSync)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }

        // Pending import card (S0111 Phase 03)
        pendingWatchSources?.let { pending ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.wear_import_pending_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.wear_import_pending_desc, pending.sources.size, pending.watchName),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(
                            onClick = { viewModel.acceptWatchImport() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.wear_import_accept))
                        }
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        OutlinedButton(
                            onClick = { viewModel.dismissWatchImport() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.wear_import_dismiss))
                        }
                    }
                }
            }
        }

        // Now Playing card (S0111 Phase 05)
        watchPlaybackState?.let { playing ->
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.wear_now_playing_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = playing.fileName,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (playing.durationMs > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (playing.positionMs.toFloat() / playing.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { viewModel.sendPlaybackCommand(WearPlaybackCommand.PREVIOUS) }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.wear_playback_previous))
                        }
                        IconButton(onClick = { viewModel.sendPlaybackCommand(WearPlaybackCommand.PLAY_PAUSE) }) {
                            Icon(
                                if (playing.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.wear_playback_play_pause)
                            )
                        }
                        IconButton(onClick = { viewModel.sendPlaybackCommand(WearPlaybackCommand.NEXT) }) {
                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.wear_playback_next))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Watch Settings section
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                CollapsibleSectionHeader(context).apply {
                    setTitle(context.getString(R.string.wear_settings_section_title))
                }
            },
            update = { header ->
                header.setTitle(header.context.getString(R.string.wear_settings_section_title))
                header.setExpanded(settingsExpanded, notify = false)
                header.setOnExpandedChangeListener { expanded ->
                    settingsExpanded = expanded
                }
            }
        )

        if (settingsExpanded) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(label = stringResource(R.string.wear_settings_audio), checked = audioEnabled) {
                audioEnabled = it
                viewModel.updateWatchSettingsLocally(buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled))
            }
            SwitchRow(label = stringResource(R.string.wear_settings_video), checked = videoEnabled) {
                videoEnabled = it
                viewModel.updateWatchSettingsLocally(buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled))
            }
            SwitchRow(label = stringResource(R.string.wear_settings_images), checked = imagesEnabled) {
                imagesEnabled = it
                viewModel.updateWatchSettingsLocally(buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled))
            }
            SwitchRow(label = stringResource(R.string.wear_settings_slideshow), checked = slideshowEnabled) {
                slideshowEnabled = it
                viewModel.updateWatchSettingsLocally(buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled))
            }
            SwitchRow(label = stringResource(R.string.wear_settings_album_art), checked = albumArtEnabled) {
                albumArtEnabled = it
                viewModel.updateWatchSettingsLocally(buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wear_settings_slideshow_interval) + ": ${slideshowInterval.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = slideshowInterval,
                onValueChange = { slideshowInterval = it },
                valueRange = 1f..3600f,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.pushSettings(
                        buildPayload(audioEnabled, videoEnabled, imagesEnabled, slideshowEnabled, slideshowInterval.toInt(), false, albumArtEnabled)
                    )
                },
                enabled = state !is WearSyncUiState.Sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wear_push_settings))
            }
        }
    }
}

private fun buildPayload(
    audio: Boolean, video: Boolean, images: Boolean,
    slideshow: Boolean, intervalSeconds: Int,
    waitForFinish: Boolean, albumArt: Boolean
) = WearSettingsPayload(
    audioEnabled = audio,
    videoEnabled = video,
    imagesEnabled = images,
    slideshowEnabled = slideshow,
    slideshowIntervalSeconds = intervalSeconds,
    slideshowWaitForFinish = waitForFinish,
    downloadAlbumArt = albumArt
)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
