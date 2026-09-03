package com.sza.fastmediasorter.wear.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSettingsItem
import com.sza.fastmediasorter.wear.ui.common.WearSettingsRow
import com.sza.fastmediasorter.wear.ui.common.WearSettingsToggleCell
import com.sza.fastmediasorter.wear.ui.common.packSettingsRows
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit

@Composable
fun OtherSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items = otherSettingsItems(uiState = uiState, viewModel = viewModel)

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(WearViewMode.GRID_2, maxWidth.value.toInt())
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Text(
                        text = stringResource(R.string.settings_group_other),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
                items(packSettingsRows(items, columns)) { row -> WearSettingsRow(row) }
            }
        }
    }
}

/**
 * The controls of this screen, built apart from the layout that renders them.
 *
 * S1949: the two toggles stay under the 32-character threshold in every locale (31 in French, 20 in
 * German), so neither declares full width. When the watch reports no rotation sensor the run holds
 * one item, and the packing rule gives that lone item the whole width by itself.
 *
 * S1862: the send-policy pair is a radio group rather than a switch, because the setting chooses
 * between two named models and a switch would have to leave one of them unnamed - "off" would say
 * nothing about the note still being kept on the watch. Both rows declare full width: a policy label
 * carries its subject as well as its choice, which is longer than a toggle caption can be in a cell.
 */
@Composable
private fun otherSettingsItems(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
): List<WearSettingsItem> {
    val albumArtLabel = stringResource(R.string.download_album_art)
    val notificationsNeededLabel =
        stringResource(R.string.wear_background_playback_needs_notifications)
    val context = LocalContext.current
    val notificationsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onBackgroundPlaybackPermissionResult(it)
        }
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    val disableAnimationsLabel = stringResource(R.string.pref_disable_animations)
    val autoRotationLabel = stringResource(R.string.wear_auto_rotation)
    val backgroundPlaybackLabel = stringResource(R.string.wear_background_playback)
    val sendAutomaticallyLabel = stringResource(R.string.wear_voice_note_policy_automatic)
    val keepOnWatchLabel = stringResource(R.string.wear_voice_note_policy_manual)
    return buildList {
        add(
            WearSettingsItem { narrow ->
                WearSettingsToggleCell(
                    label = albumArtLabel,
                    checked = uiState.downloadAlbumArt,
                    narrow = narrow,
                    onToggle = { viewModel.toggleAlbumArt() }
                )
            }
        )
        add(
            WearSettingsItem { narrow ->
                WearSettingsToggleCell(
                    label = disableAnimationsLabel,
                    checked = uiState.isAnimationsDisabled,
                    narrow = narrow,
                    onToggle = { viewModel.toggleDisableAnimations() }
                )
            }
        )
        if (uiState.hasAutoRotationSensor) {
            add(
                WearSettingsItem { narrow ->
                    WearSettingsToggleCell(
                        label = autoRotationLabel,
                        checked = uiState.isAutoRotationEnabled,
                        narrow = narrow,
                        onToggle = { viewModel.toggleAutoRotation() }
                    )
                }
            )
        }
        add(
            WearSettingsItem { narrow ->
                WearSettingsToggleCell(
                    label = backgroundPlaybackLabel,
                    checked = uiState.backgroundPlaybackEnabled,
                    narrow = narrow,
                    // S2166 (strategic criterion 9): switching it ON asks for the notification
                    // permission first, because the service's only control surface is its
                    // notification - a session the owner cannot pause without reopening the app is
                    // worse than no session. Switching it OFF never asks: nothing is left to
                    // control. The branch is written here rather than in a helper because
                    // assert-wear-settings-parity resolves this row's anchor by where its literal
                    // is drawn, and a literal inside a helper resolves to that helper's call site.
                    onToggle = {
                        if (uiState.backgroundPlaybackEnabled || notificationsAllowed) {
                            viewModel.toggleBackgroundPlayback()
                        } else {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        )
        if (uiState.backgroundPlaybackNeedsNotifications) {
            add(settingsNoticeRow(notificationsNeededLabel))
        }
        addAll(voiceNoteSendPolicyRows(uiState, viewModel, sendAutomaticallyLabel, keepOnWatchLabel))
    }
}

/** A full-width line of explanation under the row it belongs to; it is text, never a control. */
private fun settingsNoticeRow(text: String): WearSettingsItem =
    WearSettingsItem(fullWidth = true) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            textAlign = TextAlign.Center
        )
    }

/**
 * S1862's radio pair, lifted out of [otherSettingsItems] so that function stays under detekt's
 * length limit. The pair is the natural cut: it is the only run of rows on this page that answers
 * one question between them, and it sits last, so lifting it moves no row past another.
 */
@Composable
private fun voiceNoteSendPolicyRows(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    sendAutomaticallyLabel: String,
    keepOnWatchLabel: String
): List<WearSettingsItem> = listOf(
    WearSettingsItem(fullWidth = true) { narrow ->
        WearSettingsToggleCell(
            label = sendAutomaticallyLabel,
            checked = uiState.voiceNoteSendPolicy == VoiceNoteSendPolicy.AUTOMATIC,
            narrow = narrow,
            onToggle = { viewModel.setVoiceNoteSendPolicy(VoiceNoteSendPolicy.AUTOMATIC) },
            radio = true
        )
    },
    WearSettingsItem(fullWidth = true) { narrow ->
        WearSettingsToggleCell(
            label = keepOnWatchLabel,
            checked = uiState.voiceNoteSendPolicy == VoiceNoteSendPolicy.MANUAL,
            narrow = narrow,
            onToggle = { viewModel.setVoiceNoteSendPolicy(VoiceNoteSendPolicy.MANUAL) },
            radio = true
        )
    }
)
