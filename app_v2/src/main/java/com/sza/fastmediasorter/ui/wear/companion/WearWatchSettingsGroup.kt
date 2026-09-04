package com.sza.fastmediasorter.ui.wear.companion

import android.content.Context
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.ui.dialog.TooltipDialog
import com.sza.fastmediasorter.ui.settings.WearBackgroundDeliveryState
import com.sza.fastmediasorter.ui.settings.WearBackgroundPreview
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import timber.log.Timber
import java.io.File

private const val DEFAULT_SLIDESHOW_INTERVAL_SECONDS = 5
private const val DEFAULT_ANIMATIONS_DISABLED = false
private const val SLIDESHOW_INTERVAL_MAX_SECONDS = 3600f
private const val DEFAULT_PANEL_AUTO_HIDE_SECONDS = 15
private const val PANEL_AUTO_HIDE_MAX_SECONDS = 600f

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

private val BACKGROUND_MODES = listOf(
    WearSettingsPayload.BACKGROUND_MODE_NONE to R.string.wear_background_mode_none,
    WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION to R.string.wear_background_mode_animation,
    WearSettingsPayload.BACKGROUND_MODE_BRANDED_STILL to R.string.wear_background_mode_still,
    WearSettingsPayload.BACKGROUND_MODE_IMAGE to R.string.wear_background_mode_image
)

private val PICKED_IMAGE_TYPES = arrayOf("image/*")

private val PREVIEW_EDGE = 120.dp

/**
 * The watch's own settings, mirrored on the phone, as one group of the companion window.
 *
 * S2169: inside the group the rows are grouped and ordered exactly as the watch settings menu
 * shows them - Media types, Slideshow, Screen, Other - because the owner looks for a setting in the
 * same place on both surfaces. The background mode sits inside "Screen" at its canonical position
 * with its dependent picker right after it; the sync action stays at the end, outside the mirrored
 * sequence, as an action of this surface rather than a setting.
 *
 * The edited values live in [WatchSettingsState] rather than in the parent so a collapsed group
 * keeps no half-edited copy of state the watch never received.
 */
@Composable
internal fun WearWatchSettingsGroup(
    viewModel: WearSyncViewModel,
    state: WatchSettingsState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChanged: () -> Unit
) {
    Timber.d("S2169: companion watch-settings block drawn in canonical watch-menu order")
    Timber.d("S2482: companion watch settings split into separate collapsible groups")

    var mediaTypesExpanded by remember { mutableStateOf(expanded) }
    var slideshowExpanded by remember { mutableStateOf(false) }
    var screenExpanded by remember { mutableStateOf(false) }
    var otherExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(SPACING_SECTION)) {
        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_media_types),
            expanded = mediaTypesExpanded,
            onExpandedChange = { mediaTypesExpanded = it }
        ) {
            MediaTypesSwitches(state = state, onChanged = onChanged)
            SwitchRow(
                tag = "wearSwitchStreams",
                label = stringResource(R.string.wear_setting_streams_section),
                description = stringResource(R.string.wear_setting_streams_section_desc),
                checked = state.streamsSectionEnabled
            ) {
                state.streamsSectionEnabled = it
                onChanged()
            }
        }

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_slideshow),
            expanded = slideshowExpanded,
            onExpandedChange = { slideshowExpanded = it }
        ) {
            SwitchRow(
                tag = "wearSwitchSlideshow",
                label = stringResource(R.string.wear_settings_slideshow),
                description = stringResource(R.string.wear_settings_slideshow_desc),
                checked = state.slideshowEnabled
            ) {
                state.slideshowEnabled = it
                onChanged()
            }
            SlideshowIntervalSlider(
                seconds = state.slideshowInterval,
                onSecondsChange = { state.slideshowInterval = it },
                onSecondsSettled = onChanged
            )
        }

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_screen),
            expanded = screenExpanded,
            onExpandedChange = { screenExpanded = it }
        ) {
            ViewModeRow(
                tagPrefix = "wearViewMode",
                label = stringResource(R.string.wear_settings_view_mode),
                selected = state.viewMode
            ) { picked ->
                state.viewMode = picked
                onChanged()
            }
            ViewModeRow(
                tagPrefix = "wearFileListViewMode",
                label = stringResource(R.string.wear_settings_file_list_view),
                selected = state.fileListViewMode
            ) { picked ->
                state.fileListViewMode = picked
                onChanged()
            }
            BackgroundModeControls(viewModel = viewModel)
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

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_other),
            expanded = otherExpanded,
            onExpandedChange = { otherExpanded = it }
        ) {
            OtherSubgroup(state = state, onChanged = onChanged)
        }
    }
}

/**
 * One edited copy of the watch's settings.
 *
 * Held as a class rather than as a dozen locals because the payload is built from all of them at
 * once on every edit, and a builder taking them one by one is a parameter list nobody can call
 * correctly.
 */
internal class WatchSettingsState(watchSettings: WearSettingsPayload?) {
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

    // S2169: seeded with the watch's stored default for the same reason as album art - the row edits
    // a BOTH field, so an unedited push must carry the default rather than flip animations off.
    var disableAnimations by mutableStateOf(watchSettings?.disableAnimations ?: DEFAULT_ANIMATIONS_DISABLED)

    // S2166: seeded false, the watch's stored default, for the reason album art records above - the
    // row edits a BOTH field, so an unedited push must not switch background playback on by itself.
    var backgroundPlaybackEnabled by mutableStateOf(watchSettings?.backgroundPlaybackEnabled ?: false)

    // S2093: the watch's Streams row, which had no phone control at all - the one-sided setting this
    // ticket exists to remove. Default true, matching the watch's stored default.
    var streamsSectionEnabled by mutableStateOf(watchSettings?.streamsSectionEnabled ?: true)
    var viewMode by mutableStateOf(watchSettings?.viewMode ?: WEAR_VIEW_MODE_LIST)
    var fileListViewMode by mutableStateOf(watchSettings?.fileListViewMode ?: WEAR_VIEW_MODE_LIST)
    var slideshowInterval by mutableStateOf(
        (watchSettings?.slideshowIntervalSeconds ?: DEFAULT_SLIDESHOW_INTERVAL_SECONDS).toFloat()
    )
    var panelAutoHideSeconds by mutableStateOf(
        (watchSettings?.panelAutoHideSeconds ?: DEFAULT_PANEL_AUTO_HIDE_SECONDS).toFloat()
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
        streamsSectionEnabled = streamsSectionEnabled,
        disableAnimations = disableAnimations,
        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
        panelAutoHideSeconds = panelAutoHideSeconds.toInt()
    )
}

/** S2169: the watch menu's "Other" subgroup, in the watch's own row order. */
@Composable
private fun OtherSubgroup(state: WatchSettingsState, onChanged: () -> Unit) {
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
    // S2169: BOTH in the registry with no phone row before this change - the mirror was incomplete
    // without it, and the parity gate's phone side now names it.
    SwitchRow(
        tag = "wearSwitchDisableAnimations",
        label = stringResource(R.string.wear_settings_disable_animations),
        checked = state.disableAnimations
    ) {
        state.disableAnimations = it
        onChanged()
    }
    // S2166: last in the Other group, matching the watch menu - auto-rotation sits between this row
    // and animations on the watch, but it is WATCH_ONLY and has no phone row to draw here.
    SwitchRow(
        tag = "wearSwitchBackgroundPlayback",
        label = stringResource(R.string.wear_settings_background_playback),
        description = stringResource(R.string.wear_settings_background_playback_desc),
        checked = state.backgroundPlaybackEnabled
    ) {
        state.backgroundPlaybackEnabled = it
        onChanged()
    }
    // S2505: player panel auto-hide duration on watch.
    PanelAutoHideSlider(
        seconds = state.panelAutoHideSeconds,
        onSecondsChange = { state.panelAutoHideSeconds = it },
        onSecondsSettled = onChanged
    )
}

/** S2169: a subgroup heading, matching the caption style the view-mode rows already use. */
@Composable
private fun GroupCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(SPACING_TINY))
}

/** The Media types subgroup's four allowed-type toggles, held apart to keep the group's body flat. */
@Composable
private fun MediaTypesSwitches(state: WatchSettingsState, onChanged: () -> Unit) {
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
    Spacer(Modifier.height(SPACING_SMALL))
}

@Composable
private fun PanelAutoHideSlider(
    seconds: Float,
    onSecondsChange: (Float) -> Unit,
    onSecondsSettled: () -> Unit
) {
    val label = stringResource(R.string.wear_settings_panel_auto_hide)
    Text(
        text = label + ": " + seconds.toInt(),
        style = MaterialTheme.typography.bodySmall
    )
    Slider(
        value = seconds,
        onValueChange = onSecondsChange,
        onValueChangeFinished = onSecondsSettled,
        valueRange = 1f..PANEL_AUTO_HIDE_MAX_SECONDS,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wearPanelAutoHide")
            .semantics { contentDescription = label }
    )
    Spacer(Modifier.height(SPACING_SMALL))
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
    Spacer(Modifier.height(SPACING_SMALL))
}

/**
 * S2169: the watch background's two-value mode at its canonical Screen position, with the picker,
 * the preview and the delivery line appearing only under the image option, so choosing the branded
 * animation leaves the setting a single control. The two options are told apart by their labels
 * rather than by the preview, because a thumbnail is not a label for a screen reader.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundModeControls(viewModel: WearSyncViewModel) {
    val mode by viewModel.backgroundMode.collectAsState()
    val preview by viewModel.backgroundPreview.collectAsState()
    val delivery by viewModel.backgroundDelivery.collectAsState()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::sendBackgroundImage)
    }

    Text(
        text = stringResource(R.string.wear_settings_background_mode),
        style = MaterialTheme.typography.bodySmall
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        BACKGROUND_MODES.forEach { (value, labelRes) ->
            val chipLabel = stringResource(labelRes)
            FilterChip(
                selected = value == mode,
                onClick = { viewModel.updateBackgroundMode(value) },
                label = { Text(chipLabel) },
                // S2091: a chip's own label does not reach the accessibility node, so the two options
                // dump as anonymous checkboxes and the screen reader announces neither.
                modifier = Modifier
                    .testTag("wearBackgroundMode_" + value)
                    .semantics { contentDescription = chipLabel }
            )
        }
    }
    Spacer(Modifier.height(SPACING_SMALL))

    if (mode == WearSettingsPayload.BACKGROUND_MODE_IMAGE) {
        OutlinedButton(
            onClick = { pickImage.launch(PICKED_IMAGE_TYPES) },
            modifier = Modifier.testTag("wearBackgroundPickImage")
        ) {
            Text(stringResource(R.string.wear_background_pick_image))
        }
        preview?.let {
            Spacer(Modifier.height(SPACING_SMALL))
            BackgroundPreview(preview = it)
        }
        DeliveryLine(delivery = delivery)
        Spacer(Modifier.height(SPACING_SMALL))
    }
}

/**
 * Drawn through the module's own image loader rather than decoded here, and keyed by the frame's
 * stamp: every delivery overwrites the one path, so a loader keyed by path alone would keep showing
 * the picture before last.
 */
@Composable
private fun BackgroundPreview(preview: WearBackgroundPreview) {
    val description = stringResource(R.string.wear_background_preview)
    AndroidView(
        modifier = Modifier.size(PREVIEW_EDGE),
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { view ->
            view.contentDescription = description
            Glide.with(view)
                .load(File(preview.path))
                .signature(ObjectKey(preview.stamp))
                .into(view)
        }
    )
}

/**
 * Silence would read as success, and the picture is the one part of this window that travels a
 * channel able to refuse it.
 */
@Composable
private fun DeliveryLine(delivery: WearBackgroundDeliveryState) {
    val messageRes = when (delivery) {
        WearBackgroundDeliveryState.Idle -> null
        WearBackgroundDeliveryState.Sending -> R.string.wear_background_sending
        WearBackgroundDeliveryState.Sent -> R.string.wear_background_sent
        WearBackgroundDeliveryState.WatchUnreachable -> R.string.wear_background_watch_unreachable
        WearBackgroundDeliveryState.Failed -> R.string.wear_background_failed
    } ?: return

    val failed = delivery is WearBackgroundDeliveryState.WatchUnreachable ||
        delivery is WearBackgroundDeliveryState.Failed
    Spacer(Modifier.height(SPACING_TINY))
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified
    )
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
