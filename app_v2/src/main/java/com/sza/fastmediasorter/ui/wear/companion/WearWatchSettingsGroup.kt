package com.sza.fastmediasorter.ui.wear.companion

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel

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

    WearCompanionGroup(
        title = stringResource(R.string.wear_settings_section_title),
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
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

    fun payload(context: Context? = null) = WearSettingsPayload(
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
    Button(
        onClick = onPush,
        enabled = pushEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wearPushSettings")
    ) {
        Text(stringResource(R.string.wear_push_settings))
    }
}

/** The six content toggles, held apart from the rest so neither half outgrows the length ceiling. */
@Composable
private fun WatchContentSwitches(state: WatchSettingsState, onChanged: () -> Unit) {
    SwitchRow(
        tag = "wearSwitchAudio",
        label = stringResource(R.string.wear_settings_audio),
        checked = state.audioEnabled
    ) {
        state.audioEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchVideo",
        label = stringResource(R.string.wear_settings_video),
        checked = state.videoEnabled
    ) {
        state.videoEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchImages",
        label = stringResource(R.string.wear_settings_images),
        checked = state.imagesEnabled
    ) {
        state.imagesEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchSlideshow",
        label = stringResource(R.string.wear_settings_slideshow),
        checked = state.slideshowEnabled
    ) {
        state.slideshowEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchAlbumArt",
        label = stringResource(R.string.wear_settings_album_art),
        checked = state.albumArtEnabled
    ) {
        state.albumArtEnabled = it
        onChanged()
    }
    SwitchRow(
        tag = "wearSwitchKeepAwake",
        label = stringResource(R.string.wear_settings_keep_awake),
        checked = state.keepScreenAwake
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
 * The whole row is the switch, not the `Switch` inside it: the label is drawn as a sibling, so a toggle
 * left on the control announces a state with no name and TalkBack reads six identical "switch, on".
 */
@Composable
private fun SwitchRow(tag: String, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag)
            .padding(vertical = SPACING_TINY),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}
