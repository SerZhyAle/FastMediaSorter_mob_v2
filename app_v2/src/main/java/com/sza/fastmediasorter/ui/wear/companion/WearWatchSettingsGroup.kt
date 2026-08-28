package com.sza.fastmediasorter.ui.wear.companion

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import java.text.DateFormat
import java.util.Date

private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 5
private const val SLIDESHOW_INTERVAL_MAX_SECONDS = 3600f

// S2094: matches the View-side canonical row's ic_help_outline_24 - a touch target close to
// the default IconButton size with a slightly smaller glyph, per docs/ARCHITECTURE.md Pattern A.
private val SETTINGS_HELP_ICON_SIZE = 24.dp
private val SETTINGS_HELP_ICON_GLYPH_SIZE = 18.dp

// S1781: the wear module's WearViewMode enum names, mirrored here as strings - this module does not
// depend on that one, and the payload carries the name rather than an ordinal.
private const val WEAR_VIEW_MODE_LIST = "LIST"
private val WEAR_VIEW_MODES = listOf(
    WEAR_VIEW_MODE_LIST to R.string.wear_settings_view_mode_list,
    "GRID_2" to R.string.wear_settings_view_mode_grid2,
    "GRID_3" to R.string.wear_settings_view_mode_grid3
)

/**
 * The watch's own settings, mirrored on the phone, as one group of the companion window.
 *
 * The edited values live in [WatchSettingsState] rather than in the parent so a collapsed group
 * keeps no half-edited copy of state the watch never received.
 */
@Composable
fun WearWatchSettingsGroup(
    viewModel: WearSyncViewModel,
    watchSettings: WearSettingsPayload?,
    pushEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val state = remember(watchSettings) { WatchSettingsState(watchSettings) }
    // collectAsState, matching the sibling groups on this island: app_v2 does not carry
    // lifecycle-runtime-compose, and the whole island lives inside a dialog that is torn down with it.
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()

    WearCompanionGroup(
        title = stringResource(R.string.wear_settings_section_title),
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        WatchSettingsControls(
            state = state,
            pushEnabled = pushEnabled,
            lastSyncedAtEpochMillis = lastSyncedAt,
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

    // S2130: the fourth allowed-type switch. Seeded true, matching the watch's stored default, for
    // the reason the album-art line below records - an unedited push must change nothing.
    var documentsEnabled by mutableStateOf(watchSettings?.documentsEnabled ?: true)
    var slideshowEnabled by mutableStateOf(watchSettings?.slideshowEnabled ?: false)

    // S2093: seeded false because that is the watch's own stored default. Seeded true, an unedited
    // push silently turned album art on - an exchange must not change a value nobody touched.
    var albumArtEnabled by mutableStateOf(watchSettings?.downloadAlbumArt ?: false)
    var keepScreenAwake by mutableStateOf(watchSettings?.keepScreenAwakeOutsidePlayers ?: false)

    // S2093: the watch's Streams row, which had no phone control at all - the one-sided setting this
    // ticket exists to remove. Default true, matching the watch's stored default.
    var streamsSectionEnabled by mutableStateOf(watchSettings?.streamsSectionEnabled ?: true)
    var viewMode by mutableStateOf(watchSettings?.viewMode ?: WEAR_VIEW_MODE_LIST)
    var fileListViewMode by mutableStateOf(watchSettings?.fileListViewMode ?: WEAR_VIEW_MODE_LIST)
    var slideshowInterval by mutableStateOf(
        (watchSettings?.slideshowIntervalSeconds ?: DEFAULT_SLIDESHOW_INTERVAL_SECONDS).toFloat()
    )

    fun payload(context: Context? = null) = WearSettingsPayload(
        audioEnabled = audioEnabled,
        videoEnabled = videoEnabled,
        imagesEnabled = imagesEnabled,
        documentsEnabled = documentsEnabled,
        slideshowEnabled = slideshowEnabled,
        slideshowIntervalSeconds = slideshowInterval.toInt(),
        downloadAlbumArt = albumArtEnabled,
        viewMode = viewMode,
        keepScreenAwakeOutsidePlayers = keepScreenAwake,
        fileListViewMode = fileListViewMode,
        appLanguage = context?.let { LocaleHelper.getLanguage(it) },
        streamsSectionEnabled = streamsSectionEnabled
    )
}

@Composable
private fun WatchSettingsControls(
    state: WatchSettingsState,
    pushEnabled: Boolean,
    lastSyncedAtEpochMillis: Long,
    onChanged: () -> Unit,
    onPush: () -> Unit
) {
    Spacer(Modifier.height(SPACING_SMALL))
    WatchContentSwitches(state = state, onChanged = onChanged)
    Spacer(Modifier.height(SPACING_SMALL))
    ViewModeRow(
        tagPrefix = "wearViewMode",
        label = stringResource(R.string.wear_settings_view_mode),
        selected = state.viewMode
    ) { picked ->
        state.viewMode = picked
        onChanged()
    }
    Spacer(Modifier.height(SPACING_SMALL))
    ViewModeRow(
        tagPrefix = "wearFileListViewMode",
        label = stringResource(R.string.wear_settings_file_list_view),
        selected = state.fileListViewMode
    ) { picked ->
        state.fileListViewMode = picked
        onChanged()
    }
    Spacer(Modifier.height(SPACING_SMALL))
    SlideshowIntervalSlider(
        seconds = state.slideshowInterval,
        onSecondsChange = { state.slideshowInterval = it },
        onSecondsSettled = onChanged
    )
    Spacer(Modifier.height(SPACING_SMALL))
    // S2093 / ADR-1: one button per side, not two. This is the phone half of the symmetric pair - the
    // press sends this set, the watch answers with its own, and each field keeps whichever edit is
    // later. The id stays `wearPushSettings`: S2091 is parked at BlockNeedUserTest with a device note
    // that names this node, and renaming it would fail a check a human is already holding.
    Button(
        onClick = onPush,
        enabled = pushEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wearPushSettings")
    ) {
        Text(stringResource(R.string.wear_settings_sync_button))
    }
    LastSyncedCaption(lastSyncedAtEpochMillis = lastSyncedAtEpochMillis)
}

/**
 * S2093: when the two sides last agreed, read from the stored sync time rather than from the press.
 *
 * The time is written by the merge that consumed the watch's answering report, so a press that reached
 * nothing leaves the previous time standing instead of reading as a successful sync.
 */
@Composable
private fun LastSyncedCaption(lastSyncedAtEpochMillis: Long) {
    val caption = if (lastSyncedAtEpochMillis <= 0L) {
        stringResource(R.string.wear_settings_sync_never)
    } else {
        stringResource(
            R.string.wear_settings_last_synced,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(lastSyncedAtEpochMillis))
        )
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wearSyncSettingsStatus")
    )
}

/** The content toggles, held apart from the rest so neither half outgrows the length ceiling. */
@Composable
private fun WatchContentSwitches(state: WatchSettingsState, onChanged: () -> Unit) {
    SwitchRow(
        tag = "wearSwitchAudio",
        label = stringResource(R.string.wear_settings_audio),
        description = stringResource(R.string.wear_settings_audio_desc),
        checked = state.audioEnabled
    ) {
        state.audioEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchVideo",
        label = stringResource(R.string.wear_settings_video),
        description = stringResource(R.string.wear_settings_video_desc),
        checked = state.videoEnabled
    ) {
        state.videoEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchImages",
        label = stringResource(R.string.wear_settings_images),
        description = stringResource(R.string.wear_settings_images_desc),
        checked = state.imagesEnabled
    ) {
        state.imagesEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchDocuments",
        label = stringResource(R.string.wear_settings_documents),
        description = stringResource(R.string.wear_settings_documents_desc),
        checked = state.documentsEnabled
    ) {
        state.documentsEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchSlideshow",
        label = stringResource(R.string.wear_settings_slideshow),
        description = stringResource(R.string.wear_settings_slideshow_desc),
        checked = state.slideshowEnabled
    ) {
        state.slideshowEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchStreams",
        label = stringResource(R.string.wear_setting_streams_section),
        description = stringResource(R.string.wear_setting_streams_section_desc),
        checked = state.streamsSectionEnabled
    ) {
        state.streamsSectionEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchAlbumArt",
        label = stringResource(R.string.wear_settings_album_art),
        description = stringResource(R.string.wear_settings_album_art_desc),
        checked = state.albumArtEnabled,
        helpTitleRes = R.string.wear_settings_album_art_tooltip_title,
        helpMessageRes = R.string.wear_settings_album_art_tooltip_message
    ) {
        state.albumArtEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchKeepAwake",
        label = stringResource(R.string.wear_settings_keep_awake),
        description = stringResource(R.string.wear_settings_keep_awake_desc),
        checked = state.keepScreenAwake,
        helpTitleRes = R.string.wear_settings_keep_awake_tooltip_title,
        helpMessageRes = R.string.wear_settings_keep_awake_tooltip_message
    ) {
        state.keepScreenAwake = it
        onChanged()
    }
}

/**
 * [onSecondsSettled] fires once the drag ends, not on every frame: every other control here reports
 * its edit immediately, but doing that per pixel would rebuild the edited copy of the settings under
 * the moving thumb. Without it the interval was the one value the window forgot unless it was pushed.
 */
@Composable
private fun SlideshowIntervalSlider(
    seconds: Float,
    onSecondsChange: (Float) -> Unit,
    onSecondsSettled: () -> Unit
) {
    val label = stringResource(R.string.wear_settings_slideshow_interval)
    Text(
        text = label + ": " + seconds.toInt(),
        style = MaterialTheme.typography.bodySmall
    )
    Slider(
        value = seconds,
        onValueChange = onSecondsChange,
        onValueChangeFinished = onSecondsSettled,
        valueRange = 1f..SLIDESHOW_INTERVAL_MAX_SECONDS,
        // The caption above is a sibling Text, so without this the slider announces a value and no name.
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wearSlideshowInterval")
            .semantics { contentDescription = label }
    )
}

/**
 * The three values are the watch enum's own names, sent as strings: this module cannot see the wear
 * module's `WearViewMode`, and the watch resolves an unknown name back to its list default.
 *
 * S2091: the two rows on this screen hold the same three chips, so each chip's description carries the
 * row's caption - a screen reader hearing "List" twice cannot tell the watch's view mode from its
 * file-list view mode, and neither can a `tap-id` without the prefix.
 */
@Composable
private fun ViewModeRow(
    tagPrefix: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        WEAR_VIEW_MODES.forEach { (value, labelRes) ->
            val chipLabel = stringResource(labelRes)
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(chipLabel) },
                modifier = Modifier
                    .testTag(tagPrefix + "_" + value)
                    .semantics { contentDescription = label + ": " + chipLabel }
            )
        }
    }
}

/**
 * S2094: Wear Companion toggle row canonical pattern (switch left, title & description middle,
 * optional help button inline with the title - `docs/ARCHITECTURE.md` § "UI Patterns - Trigger Row").
 * The whole row is toggleable so clicking text or switch toggles the state; the help button is a
 * separate tap target with its own semantics so TalkBack announces it apart from the switch.
 *
 * [helpTitleRes]/[helpMessageRes] are supplied only for the settings whose effect is not already
 * covered by [description] alone (strategic §2 goal 3) - most rows pass neither and render no icon.
 */
@Composable
private fun SwitchRow(
    tag: String,
    label: String,
    checked: Boolean,
    description: String? = null,
    @StringRes helpTitleRes: Int? = null,
    @StringRes helpMessageRes: Int? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag)
            .padding(vertical = SPACING_SMALL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(SPACING_SMALL))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                if (helpTitleRes != null && helpMessageRes != null) {
                    val helpTitle = stringResource(helpTitleRes)
                    IconButton(
                        onClick = { TooltipDialog.show(context, helpTitleRes, helpMessageRes) },
                        modifier = Modifier
                            .size(SETTINGS_HELP_ICON_SIZE)
                            .testTag(tag + "_help")
                            .semantics { contentDescription = helpTitle }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(SETTINGS_HELP_ICON_GLYPH_SIZE)
                        )
                    }
                }
            }
            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
