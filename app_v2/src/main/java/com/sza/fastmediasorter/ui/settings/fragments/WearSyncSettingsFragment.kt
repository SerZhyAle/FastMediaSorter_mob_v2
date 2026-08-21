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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.ui.common.compose.FastMediaSorterComposeTheme
import com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.helpers.BeamAnimationDialog
import com.sza.fastmediasorter.ui.wearresources.WearResourceSelectionActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class WearSyncSettingsFragment : BottomSheetDialogFragment() {

    private val viewModel: WearSyncViewModel by viewModels()

    // Defense in depth: the sheet itself is already unreachable when the flavor lacks the companion
    // (GeneralSettingsBackupHelper.setupWearCompanionButton hides its entry point), so this gate is
    // the second one rather than the only one.
    @Inject
    lateinit var mediaCapabilities: MediaCapabilities

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
                        onPushClick = { launchBeamDialog() },
                        showResourceSelection = mediaCapabilities.supportsWearCompanion,
                        onSelectResourcesClick = { WearResourceSelectionActivity.start(requireContext()) }
                    )
                }
            }
        }

    // S1691: left collapsed, the sheet's visible height is Material's auto-peek formula
    // max(peekHeightMin, parentHeight - parentWidth * 9 / 16). In landscape the width is what that
    // formula subtracts, so nothing but the title survives and the one action this sheet exists for -
    // pushing settings to the watch - sits below the screen edge, with no hint that the sheet can be
    // dragged. Expanding explicitly drops the dependency on screen geometry; skipCollapsed keeps a
    // downward swipe from parking back at the peek height. Mirrors LauncherStartMenuFragment (S1588),
    // the one sheet in the app that had already solved this.
    override fun onStart() {
        super.onStart()
        val behavior = (dialog as? BottomSheetDialog)?.behavior ?: return
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        Timber.d("S1691: wear companion sheet opened expanded")
    }

    private fun launchBeamDialog() {
        viewModel.startPush()
        if (childFragmentManager.findFragmentByTag("beam_dialog") == null) {
            BeamAnimationDialog().show(childFragmentManager, "beam_dialog")
        }
    }
}

private val SPACING_TINY = 4.dp
private val SPACING_SMALL = 8.dp
private val SPACING_CARD = 12.dp
private val SPACING_SECTION = 16.dp
private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 5
private const val SLIDESHOW_INTERVAL_MAX_SECONDS = 3600f

// S1781: the wear module's WearViewMode enum names, mirrored here as strings - this module does not
// depend on that one, and the payload carries the name rather than an ordinal.
private const val WEAR_VIEW_MODE_LIST = "LIST"
private val WEAR_VIEW_MODES = listOf(
    WEAR_VIEW_MODE_LIST to R.string.wear_settings_view_mode_list,
    "GRID_2" to R.string.wear_settings_view_mode_grid2,
    "GRID_3" to R.string.wear_settings_view_mode_grid3
)

@Composable
private fun WearSyncScreen(
    viewModel: WearSyncViewModel,
    onPushClick: () -> Unit,
    showResourceSelection: Boolean,
    onSelectResourcesClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val watchSettings by viewModel.watchSettingsState.collectAsState()
    val pendingWatchSources by viewModel.pendingWatchSources.collectAsState()
    val watchPlaybackState by viewModel.watchPlaybackState.collectAsState()

    // S1730: the sheet opens expanded at screen height and its content is taller than that, so
    // without this everything past the fold is clipped and unreachable - the slideshow slider and
    // the push button were already lost that way before a second view row was added.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SPACING_SECTION)
            .navigationBarsPadding()
    ) {
        WearSyncHeader(lastSync = viewModel.lastSyncTimestamp)

        Button(
            onClick = onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }

        if (showResourceSelection) {
            Spacer(Modifier.height(SPACING_SMALL))
            OutlinedButton(
                onClick = onSelectResourcesClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.wear_resource_selection_title))
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

        WatchSettingsSection(
            viewModel = viewModel,
            watchSettings = watchSettings,
            pushEnabled = state !is WearSyncUiState.Sending
        )
    }
}

@Composable
private fun WearSyncHeader(lastSync: Long) {
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
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wear_import_accept))
                }
                Spacer(Modifier.padding(horizontal = SPACING_TINY))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = { onCommand(WearPlaybackCommand.PREVIOUS) }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.wear_playback_previous)
                    )
                }
                IconButton(onClick = { onCommand(WearPlaybackCommand.PLAY_PAUSE) }) {
                    Icon(
                        if (playing.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.wear_playback_play_pause)
                    )
                }
                IconButton(onClick = { onCommand(WearPlaybackCommand.NEXT) }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.wear_playback_next)
                    )
                }
            }
        }
    }
}

/**
 * The watch's own settings, mirrored on the phone.
 *
 * The edited values live here rather than in the parent so a collapsed section keeps no half-edited
 * copy of state the watch never received.
 */
/**
 * The watch's own settings, mirrored on the phone.
 *
 * The edited values live in [WatchSettingsState] rather than in the parent so a collapsed section
 * keeps no half-edited copy of state the watch never received.
 */
@Composable
private fun WatchSettingsSection(
    viewModel: WearSyncViewModel,
    watchSettings: WearSettingsPayload?,
    pushEnabled: Boolean
) {
    val context = LocalContext.current
    var settingsExpanded by remember { mutableStateOf(false) }
    val state = remember(watchSettings) { WatchSettingsState(watchSettings) }

    WatchSettingsHeader(
        expanded = settingsExpanded,
        onExpandedChange = { settingsExpanded = it }
    )

    if (settingsExpanded) {
        WatchSettingsControls(
            state = state,
            pushEnabled = pushEnabled,
            onChanged = { viewModel.updateWatchSettingsLocally(state.payload(context)) },
            onPush = { viewModel.pushSettings(state.payload(context)) }
        )
    }
}

/**
 * One edited copy of the watch's settings.
 *
 * Held as a class rather than as a dozen locals because the payload is built from all of them at
 * once on every edit, and a builder taking them one by one is a parameter list nobody can call
 * correctly.
 */
private class WatchSettingsState(watchSettings: WearSettingsPayload?) {
    var audioEnabled by mutableStateOf(watchSettings?.audioEnabled ?: true)
    var videoEnabled by mutableStateOf(watchSettings?.videoEnabled ?: true)
    var imagesEnabled by mutableStateOf(watchSettings?.imagesEnabled ?: true)
    var slideshowEnabled by mutableStateOf(watchSettings?.slideshowEnabled ?: false)
    var albumArtEnabled by mutableStateOf(watchSettings?.downloadAlbumArt ?: true)
    var keepScreenAwake by mutableStateOf(watchSettings?.keepScreenAwakeOutsidePlayers ?: false)
    var viewMode by mutableStateOf(watchSettings?.viewMode ?: WEAR_VIEW_MODE_LIST)
    var fileListViewMode by mutableStateOf(watchSettings?.fileListViewMode ?: WEAR_VIEW_MODE_LIST)
    var slideshowInterval by mutableStateOf(
        (watchSettings?.slideshowIntervalSeconds ?: DEFAULT_SLIDESHOW_INTERVAL_SECONDS).toFloat()
    )

    fun payload(context: android.content.Context? = null) = WearSettingsPayload(
        audioEnabled = audioEnabled,
        videoEnabled = videoEnabled,
        imagesEnabled = imagesEnabled,
        slideshowEnabled = slideshowEnabled,
        slideshowIntervalSeconds = slideshowInterval.toInt(),
        downloadAlbumArt = albumArtEnabled,
        viewMode = viewMode,
        keepScreenAwakeOutsidePlayers = keepScreenAwake,
        fileListViewMode = fileListViewMode,
        appLanguage = context?.let { LocaleHelper.getLanguage(it) }
    )
}

@Composable
private fun WatchSettingsControls(
    state: WatchSettingsState,
    pushEnabled: Boolean,
    onChanged: () -> Unit,
    onPush: () -> Unit
) {
    Spacer(Modifier.height(SPACING_SMALL))
    SwitchRow(label = stringResource(R.string.wear_settings_audio), checked = state.audioEnabled) {
        state.audioEnabled = it
        onChanged()
    }
    SwitchRow(label = stringResource(R.string.wear_settings_video), checked = state.videoEnabled) {
        state.videoEnabled = it
        onChanged()
    }
    SwitchRow(label = stringResource(R.string.wear_settings_images), checked = state.imagesEnabled) {
        state.imagesEnabled = it
        onChanged()
    }
    SwitchRow(label = stringResource(R.string.wear_settings_slideshow), checked = state.slideshowEnabled) {
        state.slideshowEnabled = it
        onChanged()
    }
    SwitchRow(label = stringResource(R.string.wear_settings_album_art), checked = state.albumArtEnabled) {
        state.albumArtEnabled = it
        onChanged()
    }
    SwitchRow(label = stringResource(R.string.wear_settings_keep_awake), checked = state.keepScreenAwake) {
        state.keepScreenAwake = it
        onChanged()
    }
    Spacer(Modifier.height(SPACING_SMALL))
    ViewModeRow(
        label = stringResource(R.string.wear_settings_view_mode),
        selected = state.viewMode
    ) { picked ->
        state.viewMode = picked
        onChanged()
    }
    Spacer(Modifier.height(SPACING_SMALL))
    ViewModeRow(
        label = stringResource(R.string.wear_settings_file_list_view),
        selected = state.fileListViewMode
    ) { picked ->
        state.fileListViewMode = picked
        onChanged()
    }
    Spacer(Modifier.height(SPACING_SMALL))
    SlideshowIntervalSlider(
        seconds = state.slideshowInterval,
        onSecondsChange = { state.slideshowInterval = it }
    )
    Spacer(Modifier.height(SPACING_SMALL))
    Button(
        onClick = onPush,
        enabled = pushEnabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.wear_push_settings))
    }
}

@Composable
private fun WatchSettingsHeader(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            CollapsibleSectionHeader(context).apply {
                setTitle(context.getString(R.string.wear_settings_section_title))
            }
        },
        update = { header ->
            header.setTitle(header.context.getString(R.string.wear_settings_section_title))
            header.setExpanded(expanded, notify = false)
            header.setOnExpandedChangeListener(onExpandedChange)
        }
    )
}

@Composable
private fun SlideshowIntervalSlider(seconds: Float, onSecondsChange: (Float) -> Unit) {
    Text(
        text = stringResource(R.string.wear_settings_slideshow_interval) + ": " + seconds.toInt(),
        style = MaterialTheme.typography.bodySmall
    )
    Slider(
        value = seconds,
        onValueChange = onSecondsChange,
        valueRange = 1f..SLIDESHOW_INTERVAL_MAX_SECONDS,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The three values are the watch enum's own names, sent as strings: this module cannot see the wear
 * module's `WearViewMode`, and the watch resolves an unknown name back to its list default.
 */
@Composable
private fun ViewModeRow(label: String, selected: String, onSelect: (String) -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        WEAR_VIEW_MODES.forEach { (value, labelRes) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

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
