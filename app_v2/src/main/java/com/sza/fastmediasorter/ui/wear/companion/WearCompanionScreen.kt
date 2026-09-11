package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.service.WearListenState
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

internal val SPACING_TINY = 4.dp
internal val SPACING_SMALL = 8.dp
internal val SPACING_CARD = 12.dp
internal val SPACING_SECTION = 16.dp

/** Matches the height of the label beside it, so the card does not change size while it waits. */
private val LISTEN_PROGRESS_SIZE = 24.dp

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
    onWatchResourceClick: () -> Unit,
    onOpenDocLink: (WearDocLink) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val watchSettings by viewModel.watchSettingsState.collectAsState()
    // The edited copy of the watch's settings is owned here rather than inside the collapsible group,
    // because the sync action that sends it sits outside that group (S2460).
    val context = LocalContext.current
    val watchSettingsState = remember(watchSettings) { WatchSettingsState(watchSettings) }
    // S2731: no companion-window row edits this - it rides the phone's current setting, same as appLanguage.
    val unitSystem by viewModel.unitSystem.collectAsState()
    // collectAsState, matching the sibling groups on this island: app_v2 does not carry
    // lifecycle-runtime-compose, and the island is torn down with the screen that hosts it.
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val watchAppVersion by viewModel.watchAppVersion.collectAsState()
    val pendingWatchSources by viewModel.pendingWatchSources.collectAsState()
    val watchPlaybackState by viewModel.watchPlaybackState.collectAsState()
    val listenState by viewModel.listenState.collectAsState()

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
        // The window's toolbar carries the title now (S2460); what is left here is the lead paragraph.
        Text(
            text = stringResource(R.string.wear_sync_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(SPACING_SECTION))

        Button(
            onClick = onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier.testTag("wearPushToWatch")
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }

        if (showResourceSelection) {
            ResourceActionButtons(
                onSelectResourcesClick = onSelectResourcesClick,
                onWatchResourceClick = onWatchResourceClick
            )
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

        Spacer(Modifier.height(SPACING_CARD))
        ListenToWatchCard(
            state = listenState,
            onStart = viewModel::startListening,
            onStop = viewModel::stopListening
        )

        Spacer(Modifier.height(SPACING_SECTION))

        SyncSettingsRow(
            lastSyncedAtEpochMillis = lastSyncedAt,
            watchAppVersionName = watchAppVersion,
            pushEnabled = state !is WearSyncUiState.Sending,
            onPush = {
                Timber.d("S2460: sync row push tapped from screen level")
                viewModel.pushSettings(watchSettingsState.payload(context, unitSystem))
            }
        )

        Spacer(Modifier.height(SPACING_SECTION))

        WearWatchSettingsGroup(
            viewModel = viewModel,
            state = watchSettingsState,
            onChanged = {
                viewModel.updateWatchSettingsLocally(watchSettingsState.payload(context, unitSystem))
            }
        )

        Spacer(Modifier.height(SPACING_SECTION))

        WearDocsLinkBlock(onOpenDocLink = onOpenDocLink)
    }
}

/** The two resource actions the flavor may withhold, kept together because one gate decides both. */
@Composable
private fun ResourceActionButtons(
    onSelectResourcesClick: () -> Unit,
    onWatchResourceClick: () -> Unit
) {
    Spacer(Modifier.height(SPACING_SMALL))
    OutlinedButton(
        onClick = onSelectResourcesClick,
        modifier = Modifier.testTag("wearSelectResources")
    ) {
        Text(stringResource(R.string.wear_resource_selection_title))
    }

    // S2034: the watch's own storage as a resource, added on the first tap and opened on every
    // later one - the label says both because the button is one entry point, not two.
    Spacer(Modifier.height(SPACING_SMALL))
    OutlinedButton(
        onClick = onWatchResourceClick,
        modifier = Modifier.testTag("wearWatchResource")
    ) {
        Text(stringResource(R.string.wear_companion_add_open_resource))
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
 * S2460: the sync action belongs to the whole screen, so it sits above the collapsible group rather
 * than inside it - collapsing the watch settings no longer hides the button that sends them.
 *
 * The ids stay `wearPushSettings` and `wearSyncSettingsStatus`: S2091 is parked at BlockNeedUserTest
 * with a device note naming both nodes, and renaming them would fail a check a human is holding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncSettingsRow(
    lastSyncedAtEpochMillis: Long,
    watchAppVersionName: String?,
    pushEnabled: Boolean,
    onPush: () -> Unit
) {
    // FlowRow, not Row: on a narrow screen or in a long locale the status drops onto its own line
    // under the button instead of being squeezed or clipped (strategic §3.1.2).
    FlowRow(verticalArrangement = Arrangement.Center) {
        // S2093 / ADR-1: one button per side, not two. This is the phone half of the symmetric pair -
        // the press sends this set, the watch answers with its own, and each field keeps whichever
        // edit is later.
        Button(
            onClick = onPush,
            enabled = pushEnabled,
            modifier = Modifier.testTag("wearPushSettings")
        ) {
            // The app's own watch mark, decorative: the label beside it already names the action, so
            // announcing the icon too would read it twice (strategic 2.6).
            Icon(
                painter = painterResource(R.drawable.ic_watch),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.wear_settings_sync_button))
        }
        Spacer(Modifier.width(SPACING_SMALL))
        LastSyncedCaption(
            lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
            watchAppVersionName = watchAppVersionName,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
    }
}

/**
 * S2093: when the two sides last agreed, read from the stored sync time rather than from the press.
 *
 * The time is written by the merge that consumed the watch's answering report, so a press that reached
 * nothing leaves the previous time standing instead of reading as a successful sync.
 */
@Composable
private fun LastSyncedCaption(
    lastSyncedAtEpochMillis: Long,
    watchAppVersionName: String?,
    modifier: Modifier = Modifier
) {
    val synced = lastSyncedAtEpochMillis > 0L
    // S2795: this island is outside the injection graph, so the format seam is resolved from the
    // context. The system is read as state rather than once, so flipping the setting behind this
    // window recomposes the caption instead of leaving the previous clock length standing.
    val context = LocalContext.current
    val formatSeam = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, UnitSystemEntryPoint::class.java)
    }
    val unitSystem by formatSeam.unitSystemProvider().current.collectAsState()
    val caption = if (!synced) {
        stringResource(R.string.wear_settings_sync_never)
    } else {
        stringResource(
            R.string.wear_settings_last_synced,
            formatSeam.quantityFormatter()
                .format(Quantity.DateTime(lastSyncedAtEpochMillis), unitSystem)
        )
    }
    Column(modifier = modifier) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("wearSyncSettingsStatus")
        )
        // S2461: shown only after an exchange has completed - before the first one there is no version
        // to be unknown about, and an "unknown" line on a never-synced pair reads as a fault rather than
        // as the absence of an answer (strategic 2.5).
        if (synced) {
            WatchVersionCaption(watchAppVersionName = watchAppVersionName)
        }
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
    Timber.d("S2461: version line drawn - watch=$watchAppVersionName phone=$phoneVersion mismatched=$mismatched")
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
