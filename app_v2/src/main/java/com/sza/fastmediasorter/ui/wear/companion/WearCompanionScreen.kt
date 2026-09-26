package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.service.WearListenState
import com.sza.fastmediasorter.ui.settings.ClipboardSendOutcomeText
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel

internal val SPACING_TINY = 4.dp
internal val SPACING_SMALL = 8.dp
internal val SPACING_CARD = 12.dp
internal val SPACING_SECTION = 16.dp

/** Matches the height of the label beside it, so the card does not change size while it waits. */
private val LISTEN_PROGRESS_SIZE = 24.dp

/**
 * S2000: the companion window's frame - the operations group, then the settings groups.
 *
 * The frame holds no setting of its own. Every setting belongs to a group, and a group is one call
 * here plus one file, which is what makes "add the next watch setting" an addition rather than a
 * rebuild (strategic §2.4, §5.1 pillar A).
 *
 * S3185: the settings sync action is not in the island - it sits in the window's toolbar, which keeps
 * S2000's rule that the button sending edits never hides inside a collapsed group. The toolbar only
 * asks; the island answers here, because the edited copy of the settings is owned here and the
 * payload is built in one place.
 *
 * S2091: every control below carries a `testTag`, which reaches `uiautomator` as a `resource-id` only
 * because `FastMediaSorterComposeTheme` sets `testTagsAsResourceId` for every island (S2096). Do not
 * repeat that flag here - one owner keeps a removal visible instead of silently sparing this screen.
 */
@Composable
fun WearCompanionScreen(
    viewModel: WearSyncViewModel,
    faceSlotsViewModel: WearFaceSlotsViewModel,
    onPushClick: () -> Unit,
    showResourceSelection: Boolean,
    onSelectResourcesClick: () -> Unit,
    onWatchResourceClick: () -> Unit,
    onOpenDocLink: (WearDocLink) -> Unit
) {
    val watchSettings by viewModel.watchSettingsState.collectAsState()
    val context = LocalContext.current
    val watchSettingsState = remember(watchSettings) { WatchSettingsState(watchSettings) }
    // S2731: no companion-window row edits this - it rides the phone's current setting, same as appLanguage.
    val unitSystem by viewModel.unitSystem.collectAsState()
    // S3330: same shape as unitSystem above - dimClockOverlayEnabled is BOTH and merges,
    // dimClockSecondsVisible stays PHONE_ONLY like unitSystem.
    val dimClockOverlayEnabled by viewModel.dimClockOverlayEnabled.collectAsState()
    val dimClockSecondsVisible = viewModel.dimClockSecondsVisible

    // Read at the moment of the request rather than captured by the effect, so an edit does not
    // restart the collector and drop a press that lands during the restart.
    val currentPayload by rememberUpdatedState {
        watchSettingsState.payload(context, unitSystem, dimClockOverlayEnabled, dimClockSecondsVisible)
    }
    LaunchedEffect(viewModel) {
        viewModel.settingsPushRequests.collect {
            viewModel.pushSettings(currentPayload())
        }
    }

    // The content is taller than the window on a short phone, and before this the slideshow slider
    // and the push button were the parts that fell past the fold (S1730).
    // S2460: the shell owns both insets now - `activity_wear_companion.xml` sets fitsSystemWindows on
    // its CoordinatorLayout, which pads the content area away from the status bar and the navigation
    // bar alike, so a second navigationBarsPadding() here would sit on top of that one.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SPACING_SECTION)
    ) {
        WearOperationsGroup(
            viewModel = viewModel,
            actions = OperationsActions(
                onPushClick = onPushClick,
                showResourceSelection = showResourceSelection,
                onSelectResourcesClick = onSelectResourcesClick,
                onWatchResourceClick = onWatchResourceClick
            )
        )

        Spacer(Modifier.height(SPACING_SECTION))

        // S3558: its own group rather than rows of the watch settings below - those travel with the
        // settings push, while a button pick reaches the watch face by itself.
        WearFaceSlotsGroup(viewModel = faceSlotsViewModel)

        Spacer(Modifier.height(SPACING_SECTION))

        WearWatchSettingsGroup(
            viewModel = viewModel,
            state = watchSettingsState,
            onChanged = {
                viewModel.updateWatchSettingsLocally(
                    watchSettingsState.payload(context, unitSystem, dimClockOverlayEnabled, dimClockSecondsVisible)
                )
            }
        )

        Spacer(Modifier.height(SPACING_SECTION))

        WearDocsLinkBlock(onOpenDocLink = onOpenDocLink)
    }
}

/** The host's callbacks for the operations group, bundled so the group's signature stays readable. */
private class OperationsActions(
    val onPushClick: () -> Unit,
    val showResourceSelection: Boolean,
    val onSelectResourcesClick: () -> Unit,
    val onWatchResourceClick: () -> Unit
)

/**
 * S3185: everything the window showed above the settings groups, gathered into one collapsible group.
 *
 * It starts expanded, unlike the settings groups: it carries live cards - the watch's offer to hand
 * sources back, what the watch is playing, listening - and a collapsed group would hide an offer the
 * owner has to answer.
 */
@Composable
private fun WearOperationsGroup(viewModel: WearSyncViewModel, actions: OperationsActions) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    WearCompanionGroup(
        title = stringResource(R.string.wear_companion_group_operations),
        summary = null,
        expanded = expanded,
        tag = "wearGroupOperations",
        onExpandedChange = { expanded = it }
    ) {
        WearCompanionTwoColumnArranger {
            WearOperationsItems(viewModel = viewModel, actions = actions)
        }
    }
}

/**
 * Each item is exactly one layout node: the arranger places nodes into cells, so a stray spacer would
 * take a cell of its own and shift every item after it into the other column.
 */
@Composable
private fun WearOperationsItems(viewModel: WearSyncViewModel, actions: OperationsActions) {
    val state by viewModel.uiState.collectAsState()
    // collectAsState, matching the sibling groups on this island: app_v2 does not carry
    // lifecycle-runtime-compose, and the island is torn down with the screen that hosts it.
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val watchAppVersion by viewModel.watchAppVersion.collectAsState()
    val pendingWatchSources by viewModel.pendingWatchSources.collectAsState()
    val watchPlaybackState by viewModel.watchPlaybackState.collectAsState()
    val listenState by viewModel.listenState.collectAsState()
    val clipboardSending by viewModel.clipboardSendInFlight.collectAsState()
    val clipboardOutcome by viewModel.clipboardSendOutcome.collectAsState()

    OperationCell {
        Text(
            text = stringResource(R.string.wear_sync_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OperationCell {
        Button(
            onClick = actions.onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier.testTag("wearPushToWatch")
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }
    }
    if (actions.showResourceSelection) {
        ResourceActionButtons(
            onSelectResourcesClick = actions.onSelectResourcesClick,
            onWatchResourceClick = actions.onWatchResourceClick
        )
    }
    pendingWatchSources?.let { pending ->
        OperationCell {
            PendingImportCard(
                pending = pending,
                onAccept = { viewModel.acceptWatchImport() },
                onDismiss = { viewModel.dismissWatchImport() }
            )
        }
    }
    watchPlaybackState?.let { playing ->
        OperationCell { NowPlayingCard(playing = playing, onCommand = viewModel::sendPlaybackCommand) }
    }
    OperationCell {
        ListenToWatchCard(
            state = listenState,
            onStart = viewModel::startListening,
            onStop = viewModel::stopListening
        )
    }
    OperationCell { SendClipboardRow(clipboardSending, clipboardOutcome, viewModel::sendClipboardToWatch) }
    OperationCell { RequestScreenshotRow(viewModel) }
    // S2461: shown only after an exchange has completed - before the first one there is no version
    // to be unknown about, and an "unknown" line on a never-synced pair reads as a fault rather than
    // as the absence of an answer (strategic 2.5).
    if (lastSyncedAt > 0L) {
        OperationCell { WatchVersionCaption(watchAppVersionName = watchAppVersion) }
    }
}

@Composable
private fun OperationCell(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SPACING_TINY)
    ) {
        content()
    }
}

/**
 * S3109: hands this phone's text clipboard to the watch, and says underneath what came of it.
 *
 * There is no "fetch the watch clipboard" counterpart here and there cannot be: since Android 10 only
 * the foreground app may read its own clipboard, so a watch asked for its clipboard from here would be
 * asked while it is not in front of anyone (ADR-1). The watch sends its own clipboard from its
 * Clipboard program instead, and it lands on this phone without this screen being open.
 */
@Composable
private fun SendClipboardRow(
    sending: Boolean,
    outcome: ClipboardSendOutcomeText?,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onSend,
            enabled = !sending,
            modifier = Modifier.testTag("wearSendClipboard")
        ) {
            Text(stringResource(R.string.wear_clipboard_send_to_watch))
        }
        if (outcome != null && !sending) {
            Text(
                text = if (outcome.arg == null) {
                    stringResource(outcome.res)
                } else {
                    stringResource(outcome.res, outcome.arg)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SPACING_SMALL)
            )
        }
    }
}

/**
 * S3110: asks the watch to photograph its own screen, and says underneath what came of it.
 *
 * What arrives is the watch app's screen, never the watch face or another app: reaching those needs
 * MediaProjection, whose consent dialog would have to be tapped on the watch for every request and
 * would take the initiative back off this phone (ADR-1). The picture itself does not land here - it
 * travels the ordinary watch-file route and is announced by its own notification.
 */
@Composable
private fun RequestScreenshotRow(viewModel: WearSyncViewModel) {
    // Read here rather than beside the screen's other state: the host composable is at detekt's length
    // ceiling, and these two values are read by nothing above this row.
    val requesting by viewModel.screenshotRequestInFlight.collectAsState()
    val outcome by viewModel.screenshotRequestOutcome.collectAsState()
    val onRequest = viewModel::requestWatchScreenshot

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onRequest,
            enabled = !requesting,
            modifier = Modifier.testTag("wearRequestScreenshot")
        ) {
            Text(stringResource(R.string.wear_screenshot_request))
        }
        val line = outcome
        if (line != null && !requesting) {
            Text(
                text = if (line.arg == null) {
                    stringResource(line.res)
                } else {
                    stringResource(line.res, line.arg)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SPACING_SMALL)
            )
        }
    }
}

/** The two resource actions the flavor may withhold, kept together because one gate decides both. */
@Composable
private fun ResourceActionButtons(
    onSelectResourcesClick: () -> Unit,
    onWatchResourceClick: () -> Unit
) {
    OperationCell {
        OutlinedButton(
            onClick = onSelectResourcesClick,
            modifier = Modifier.testTag("wearSelectResources")
        ) {
            Text(stringResource(R.string.wear_resource_selection_title))
        }
    }

    // S2034: the watch's own storage as a resource, added on the first tap and opened on every
    // later one - the label says both because the button is one entry point, not two.
    OperationCell {
        OutlinedButton(
            onClick = onWatchResourceClick,
            modifier = Modifier.testTag("wearWatchResource")
        ) {
            Text(stringResource(R.string.wear_companion_add_open_resource))
        }
    }
}

/**
 * S2460: where to read more about the watch, in the shape Settings -> General ends with - text
 * buttons with a leading icon, wrapping onto as many lines as the width needs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WearDocsLinkBlock(onOpenDocLink: (WearDocLink) -> Unit) {
    FlowRow {
        DocLinkButton(
            iconRes = R.drawable.ic_watch,
            labelRes = R.string.settings_wear_web_portal_button,
            testTag = "wearDocsPortal",
            onClick = { onOpenDocLink(WearDocLink.PORTAL) }
        )
        DocLinkButton(
            iconRes = R.drawable.ic_open_in_browse,
            labelRes = R.string.settings_wear_install_guide_button,
            testTag = "wearDocsInstallGuide",
            onClick = { onOpenDocLink(WearDocLink.INSTALL_GUIDE) }
        )
    }
}

@Composable
private fun DocLinkButton(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    testTag: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        // Decorative: the label beside it says where the link goes.
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(labelRes))
    }
}

/**
 * S2461: which build on the watch accepted the last completed sync.
 *
 * Label and value are one string rather than two adjacent texts, so a screen reader announces them as
 * one phrase (strategic 3.2 "Доступность"). A watch build from a different day than this phone's is drawn
 * in the error colour because a mismatched pair is the common cause of the odd behaviour this readout
 * exists to make diagnosable (strategic 3.1.2).
 */
@Composable
private fun WatchVersionCaption(watchAppVersionName: String?) {
    val phoneVersion = BuildConfig.VERSION_NAME
    val mismatched = isWatchVersionMismatched(watch = watchAppVersionName, phone = phoneVersion)
    val caption = when {
        watchAppVersionName.isNullOrBlank() -> stringResource(R.string.wear_settings_watch_version_unknown)
        mismatched -> stringResource(
            R.string.wear_settings_watch_version_mismatch,
            watchAppVersionName,
            phoneVersion
        )
        else -> stringResource(R.string.wear_settings_watch_version, watchAppVersionName)
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = if (mismatched) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.testTag("wearWatchVersion")
    )
}

/**
 * S2461: whether the two halves of the pair were built from different generations.
 *
 * The halves are packaged by two separate builds minutes apart, so the stamp alone always differs and
 * comparing whole version names paints every pair as mismatched, including a pair built from one revision
 * (S2861 run 4). The comparison is therefore on the build DATE - the coarsest thing the stamp still states
 * plainly. Two builds of one revision share it; a watch left on an older build does not, and that older
 * build is the case this readout exists to catch (strategic 1). Comparing the major.minor pair instead
 * would read as the calendar YEAR in this format and green every watch build of the same year.
 */
internal fun isWatchVersionMismatched(watch: String?, phone: String): Boolean {
    if (watch.isNullOrBlank()) return false
    return extractVersionGeneration(watch) != extractVersionGeneration(phone)
}

/**
 * S2461: the generation key of a version name - the build date for this project's stamped format, the
 * major.minor pair for anything else.
 *
 * The stamped format is `Y.YM.MDDH.Hmm` (`app_v2/build.gradle.kts`), so "2.60.9110.137-NoLegal-DEBUG" is
 * 2026-09-11 01:37 and its key is "260911". A name carrying no stamp - a hand-set version on an old build -
 * has no date to read, and keying it by major.minor keeps it apart from a stamped one instead of
 * collapsing every shape into one bucket.
 */
internal fun extractVersionGeneration(version: String): String {
    val clean = version.substringBefore('-').substringBefore('+').trim()
    val parts = clean.split('.')
    val fallback = if (parts.size >= 2 && parts[0].isNotEmpty()) "${parts[0]}.${parts[1]}" else clean
    return stampedBuildDateOrNull(parts) ?: fallback
}

/**
 * S2461: "2.60.9110.137" -> "260911". The last digit of the third group is the hour's first digit, which
 * belongs to the time rather than to the date, so it is dropped. Null when the name is not a stamp.
 */
private fun stampedBuildDateOrNull(parts: List<String>): String? {
    val stamped = parts.size == STAMPED_GROUP_COUNT &&
        parts.all { it.isNotEmpty() && it.all(Char::isDigit) } &&
        parts[0].length == 1 &&
        parts[1].length == 2 &&
        parts[2].length == STAMPED_DATE_GROUP_LENGTH
    return if (stamped) parts[0] + parts[1] + parts[2].dropLast(1) else null
}

/** S2461: `Y.YM.MDDH.Hmm` - four dot-separated groups, the third of them four digits wide. */
private const val STAMPED_GROUP_COUNT = 4
private const val STAMPED_DATE_GROUP_LENGTH = 4

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

/**
 * S2550: hearing what the watch's microphone hears, and the only way to ask for it.
 *
 * A card, because listening is live state about the watch like the two above it - but unlike them it
 * is always drawn, since a control that appeared only once a session existed could never begin one.
 * The three appearances are the three states and nothing else: idle offers the start, waiting shows
 * that the request is with the owner's wrist, and listening offers the stop.
 */
@Composable
private fun ListenToWatchCard(
    state: WearListenState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(SPACING_CARD)) {
            Text(
                text = stringResource(R.string.wear_listen_title),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(SPACING_TINY))
            Text(text = stringResource(listenCaptionOf(state)), style = MaterialTheme.typography.bodySmall)
            (state as? WearListenState.Idle)?.messageRes?.let { messageRes ->
                Spacer(Modifier.height(SPACING_TINY))
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("wearListenMessage")
                )
            }
            Spacer(Modifier.height(SPACING_SMALL))
            ListenAction(state = state, onStart = onStart, onStop = onStop)
        }
    }
}

@Composable
private fun ListenAction(state: WearListenState, onStart: () -> Unit, onStop: () -> Unit) {
    when (state) {
        is WearListenState.Idle -> Button(
            onClick = onStart,
            modifier = Modifier.testTag("wearListenStart")
        ) {
            Text(stringResource(R.string.wear_listen_start))
        }
        // No cancel beside it: the request lives on the watch now, and the watch is where it is
        // declined or left to expire - a phone-side cancel would be a second answer to one question.
        WearListenState.Awaiting -> CircularProgressIndicator(
            modifier = Modifier
                .size(LISTEN_PROGRESS_SIZE)
                .testTag("wearListenWaiting")
        )
        is WearListenState.Listening -> OutlinedButton(
            onClick = onStop,
            modifier = Modifier.testTag("wearListenStop")
        ) {
            Text(stringResource(R.string.wear_listen_stop))
        }
    }
}

@StringRes
private fun listenCaptionOf(state: WearListenState): Int = when (state) {
    is WearListenState.Idle -> R.string.wear_listen_caption_idle
    WearListenState.Awaiting -> R.string.wear_listen_caption_waiting
    is WearListenState.Listening -> R.string.wear_listen_caption_listening
}
