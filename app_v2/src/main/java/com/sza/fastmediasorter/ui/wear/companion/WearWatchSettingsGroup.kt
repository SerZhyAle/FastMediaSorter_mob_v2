package com.sza.fastmediasorter.ui.wear.companion

import android.content.Context
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
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
import com.sza.fastmediasorter.domain.model.PowerSavingTrigger
import com.sza.fastmediasorter.domain.model.UnitSystem
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

// S2866: the discrete intervals the watch side already offers (wear OtherSettingsScreen.kt
// PANEL_AUTO_HIDE_INTERVALS). The phone companion previously used a 1-600 slider, which put
// useful values in the left tenth of the track; chips match both the watch and the neighboring
// power-saving threshold row in this same group.
private const val THREE_SECONDS = 3
private const val FIVE_SECONDS = 5
private const val TEN_SECONDS = 10
private const val FIFTEEN_SECONDS = 15
private const val TWENTY_SECONDS = 20
private const val THIRTY_SECONDS = 30
private const val SIXTY_SECONDS = 60
private val PANEL_AUTO_HIDE_INTERVALS = listOf(
    THREE_SECONDS,
    FIVE_SECONDS,
    TEN_SECONDS,
    FIFTEEN_SECONDS,
    TWENTY_SECONDS,
    THIRTY_SECONDS,
    SIXTY_SECONDS
)

// S2866: snap a previously saved value to the nearest valid interval so a value above the old
// 600-second ceiling or between intervals opens without error (strategic §3.2 data compatibility).
private fun coercePanelAutoHide(seconds: Int): Int =
    PANEL_AUTO_HIDE_INTERVALS.minByOrNull { kotlin.math.abs(it - seconds) }
        ?: PANEL_AUTO_HIDE_INTERVALS.first()

// S2094: matches the View-side canonical row's ic_help_outline_24 - a touch target close to
// the default IconButton size with a slightly smaller glyph, per docs/ARCHITECTURE.md Pattern A.
private val SETTINGS_HELP_ICON_SIZE = 24.dp
private val SETTINGS_HELP_ICON_GLYPH_SIZE = 18.dp

// S1781/S2643: the label table for the wear module's WearViewMode names. The names themselves live
// in the WearSettingsPayload companion beside the background modes and the colour schemes, so all
// three vocabularies crossing this wire are declared once and in one form.
private val WEAR_VIEW_MODES = listOf(
    WearSettingsPayload.VIEW_MODE_LIST to R.string.wear_settings_view_mode_list,
    WearSettingsPayload.VIEW_MODE_GRID_2 to R.string.wear_settings_view_mode_grid2,
    WearSettingsPayload.VIEW_MODE_GRID_3 to R.string.wear_settings_view_mode_grid3
)

private val BACKGROUND_MODES = listOf(
    WearSettingsPayload.BACKGROUND_MODE_NONE to R.string.wear_background_mode_none,
    WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION to R.string.wear_background_mode_animation,
    WearSettingsPayload.BACKGROUND_MODE_BRANDED_STILL to R.string.wear_background_mode_still,
    WearSettingsPayload.BACKGROUND_MODE_IMAGE to R.string.wear_background_mode_image
)

// S2522: the watch's eight schemes, in the order the watch itself lists them. Eight entries and no
// AUTO - Wear OS has no system light/dark switch, so a follow-the-system option could never differ
// from the dark scheme (strategic ADR-2).
private val COLOR_SCHEMES = listOf(
    WearSettingsPayload.COLOR_SCHEME_DARK to R.string.wear_color_scheme_dark,
    WearSettingsPayload.COLOR_SCHEME_LIGHT to R.string.wear_color_scheme_light,
    WearSettingsPayload.COLOR_SCHEME_DARK_GREEN to R.string.wear_color_scheme_dark_green,
    WearSettingsPayload.COLOR_SCHEME_DARK_BLUE to R.string.wear_color_scheme_dark_blue,
    WearSettingsPayload.COLOR_SCHEME_DARK_RED to R.string.wear_color_scheme_dark_red,
    WearSettingsPayload.COLOR_SCHEME_LIGHT_GREEN to R.string.wear_color_scheme_light_green,
    WearSettingsPayload.COLOR_SCHEME_LIGHT_BLUE to R.string.wear_color_scheme_light_blue,
    WearSettingsPayload.COLOR_SCHEME_LIGHT_RED to R.string.wear_color_scheme_light_red
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
    onChanged: () -> Unit
) {
    Timber.d("S2169: companion watch-settings block drawn in canonical watch-menu order")
    Timber.d("S2482: companion watch settings split into separate collapsible groups")

    // S2643: each subgroup owns its own expansion since S2482 split the block into four; the outer
    // expansion parameter that used to seed this one was never written by anyone and is gone.
    var mediaTypesExpanded by remember { mutableStateOf(false) }
    var slideshowExpanded by remember { mutableStateOf(false) }
    var screenExpanded by remember { mutableStateOf(false) }
    var otherExpanded by remember { mutableStateOf(false) }

    // S2865: one value summary per group, drawn under the title in the collapsed as well as the
    // expanded state - a collapsed group used to name its topic and say nothing about what the
    // watch holds. Recomputed from the same state the rows inside edit, so a watch answer updates
    // the collapsed line too.
    val summaries = groupSummaries(viewModel, state)

    Column(verticalArrangement = Arrangement.spacedBy(SPACING_SECTION)) {
        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_media_types),
            summary = summaries.mediaTypes,
            expanded = mediaTypesExpanded,
            tag = "wearGroupMediaTypes",
            onExpandedChange = { mediaTypesExpanded = it }
        ) {
            MediaTypesSwitches(state = state, onChanged = onChanged)
            StreamsSectionSwitch(state = state, onChanged = onChanged)
        }

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_slideshow),
            summary = summaries.slideshow,
            expanded = slideshowExpanded,
            tag = "wearGroupSlideshow",
            onExpandedChange = { slideshowExpanded = it }
        ) {
            SlideshowSwitch(state = state, onChanged = onChanged)
            SlideshowIntervalSlider(
                seconds = state.slideshowInterval,
                onSecondsChange = { state.slideshowInterval = it },
                onSecondsSettled = onChanged
            )
        }

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_screen),
            summary = summaries.screen,
            expanded = screenExpanded,
            tag = "wearGroupScreen",
            onExpandedChange = { screenExpanded = it }
        ) {
            ViewModeRows(state = state, onChanged = onChanged)
            BackgroundModeControls(viewModel = viewModel)
            ColorSchemeControls(viewModel = viewModel)
            KeepAwakeSwitch(state = state, onChanged = onChanged)
        }

        WearCompanionGroup(
            title = stringResource(R.string.wear_settings_group_other),
            summary = summaries.other,
            expanded = otherExpanded,
            tag = "wearGroupOther",
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

    // S2536: seeded with the watch's own stored default for the reason album art records above - the
    // row edits a BOTH field, so an unedited push must carry the default rather than turn the watch's
    // power saving off behind the owner's back.
    var powerSavingTrigger by mutableStateOf(
        watchSettings?.powerSavingTrigger ?: PowerSavingTrigger.DEFAULT.name
    )

    // S2166: seeded false, the watch's stored default, for the reason album art records above - the
    // row edits a BOTH field, so an unedited push must not switch background playback on by itself.
    var backgroundPlaybackEnabled by mutableStateOf(watchSettings?.backgroundPlaybackEnabled ?: false)

    // S2093: the watch's Streams row, which had no phone control at all - the one-sided setting this
    // ticket exists to remove. Default true, matching the watch's stored default.
    var streamsSectionEnabled by mutableStateOf(watchSettings?.streamsSectionEnabled ?: true)
    var viewMode by mutableStateOf(watchSettings?.viewMode ?: WearSettingsPayload.VIEW_MODE_LIST)
    var fileListViewMode by mutableStateOf(watchSettings?.fileListViewMode ?: WearSettingsPayload.VIEW_MODE_LIST)
    var slideshowInterval by mutableStateOf(
        (watchSettings?.slideshowIntervalSeconds ?: DEFAULT_SLIDESHOW_INTERVAL_SECONDS).toFloat()
    )
    var panelAutoHideSeconds by mutableStateOf(
        coercePanelAutoHide(watchSettings?.panelAutoHideSeconds ?: DEFAULT_PANEL_AUTO_HIDE_SECONDS)
    )

    fun payload(context: Context? = null, unitSystem: UnitSystem? = null) = WearSettingsPayload(
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
        powerSavingTrigger = powerSavingTrigger,
        backgroundPlaybackEnabled = backgroundPlaybackEnabled,
        panelAutoHideSeconds = panelAutoHideSeconds,
        // S2731: no companion-window row exists for this field (PHONE_ONLY, no companionRowTag) - it
        // rides the phone's current AppSettings the same way appLanguage rides the current locale.
        unitSystem = unitSystem?.name
    )
}

/** S2169: the watch menu's "Other" subgroup, in the watch's own row order. */
@OptIn(ExperimentalFoundationApi::class)
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
    // S2865: the description closes the subtitle gap - its neighbours above and below both carry
    // one, and the one bare row read as the odd one out.
    SwitchRow(
        tag = "wearSwitchDisableAnimations",
        label = stringResource(R.string.wear_settings_disable_animations),
        description = stringResource(R.string.wear_settings_disable_animations_desc),
        checked = state.disableAnimations
    ) {
        state.disableAnimations = it
        onChanged()
    }
    // S2536: the watch's power-saving threshold, directly after the animation switch it strengthens -
    // the same neighbouring the watch menu uses. Chips rather than a switch because the value is a
    // threshold, and the registry declares it BOTH, so this phone row is what the parity gate
    // requires to exist.
    //
    // Written inline rather than extracted into its own composable, unlike the background-mode chips
    // below: the order gate resolves a row literal to the FIRST INVOCATION of the helper holding it,
    // one level only. A helper nested inside this one would resolve to its call site here while its
    // sibling rows resolve to where THIS subgroup is invoked, which sorts the row after every row it
    // is drawn before.
    Text(
        text = stringResource(R.string.wear_settings_power_saving),
        style = MaterialTheme.typography.bodySmall
    )
    // S2865: one horizontal line instead of a wrapping FlowRow - the six thresholds are one ranked
    // scale, and the wrapped second row outweighed the rest of the group. A narrow screen or a long
    // locale scrolls rather than wraps.
    // S2924: bring selected chip into view on initial open or when selection changes, so the active
    // threshold is not hidden beyond the right scroll edge.
    val powerSavingScrollState = rememberScrollState()
    val powerSavingBringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.powerSavingTrigger) {
        Timber.d("S2924: power saving row scrolled to selected trigger %s", state.powerSavingTrigger)
        powerSavingBringIntoViewRequester.bringIntoView()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(powerSavingScrollState),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        POWER_SAVING_TRIGGERS.forEach { (value, labelRes) ->
            val chipLabel = stringResource(labelRes)
            val isSelected = value == state.powerSavingTrigger
            FilterChip(
                selected = isSelected,
                onClick = {
                    state.powerSavingTrigger = value
                    onChanged()
                },
                label = { Text(chipLabel) },
                // S2091: a chip's own label does not reach the accessibility node, so without this the
                // options dump as anonymous checkboxes and the screen reader announces none of them.
                modifier = Modifier
                    .testTag("wearPowerSavingTrigger_" + value)
                    .then(
                        if (isSelected) Modifier.bringIntoViewRequester(powerSavingBringIntoViewRequester) else Modifier
                    )
                    .semantics { contentDescription = chipLabel }
            )
        }
    }
    // The watch judges its own charge, because the two devices have separate batteries and a phone at
    // eighty percent says nothing about a watch at twelve (ADR-4). Said here so the row does not read
    // as a phone-side switch.
    Text(
        text = stringResource(R.string.wear_settings_power_saving_desc),
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(SPACING_SMALL))
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
    // S2505: player panel auto-hide duration on watch. S2866: chips replace the 1-600 slider,
    // matching the watch's discrete intervals and the neighboring power-saving chips above.
    PanelAutoHideChips(
        seconds = state.panelAutoHideSeconds,
        onSecondsChange = { state.panelAutoHideSeconds = it },
        onSecondsSettled = onChanged
    )
}

/** S2865: the four per-group value summaries, held together so the block composes them once. */
private class WearGroupSummaries(
    val mediaTypes: String,
    val slideshow: String,
    val screen: String,
    val other: String
)

@Composable
private fun groupSummaries(viewModel: WearSyncViewModel, state: WatchSettingsState): WearGroupSummaries {
    val backgroundMode by viewModel.backgroundMode.collectAsState()
    val colorScheme by viewModel.colorScheme.collectAsState()
    return WearGroupSummaries(
        mediaTypes = mediaTypesSummaryLine(state),
        slideshow = slideshowSummaryLine(state),
        screen = screenSummaryLine(backgroundMode, colorScheme, state),
        other = otherSummaryLine(state)
    )
}

/**
 * S2865: the one-line value summary each group shows under its title, so the collapsed window
 * answers "what is on the watch now" without a single expansion. Each stays short on purpose -
 * the header ellipsizes to one line, and the rows inside carry the detail. Rows with two states
 * announce themselves as "label: On/Off"; enum values speak through their own chip labels.
 */
@Composable
private fun mediaTypesSummaryLine(state: WatchSettingsState): String {
    val enabled = buildList {
        if (state.audioEnabled) add(stringResource(R.string.wear_settings_audio))
        if (state.videoEnabled) add(stringResource(R.string.wear_settings_video))
        if (state.imagesEnabled) add(stringResource(R.string.wear_settings_images))
        if (state.documentsEnabled) add(stringResource(R.string.wear_settings_documents))
        if (state.streamsSectionEnabled) add(stringResource(R.string.wear_setting_streams_section))
    }
    return if (enabled.isEmpty()) {
        stringResource(R.string.wear_companion_summary_none)
    } else {
        enabled.joinToString(", ")
    }
}

@Composable
private fun slideshowSummaryLine(state: WatchSettingsState): String =
    if (state.slideshowEnabled) {
        stringResource(R.string.wear_companion_summary_slideshow_on, state.slideshowInterval.toInt())
    } else {
        stringResource(R.string.wear_companion_summary_off)
    }

@Composable
private fun screenSummaryLine(
    backgroundMode: String,
    colorScheme: String,
    state: WatchSettingsState
): String = listOfNotNull(
    labelFor(WEAR_VIEW_MODES, state.viewMode),
    labelFor(BACKGROUND_MODES, backgroundMode),
    labelFor(COLOR_SCHEMES, colorScheme)
).joinToString(", ")

@Composable
private fun otherSummaryLine(state: WatchSettingsState): String = listOf(
    stringResource(R.string.wear_settings_album_art) + ": " + onOffWord(state.albumArtEnabled),
    labelFor(POWER_SAVING_TRIGGERS, state.powerSavingTrigger)
        ?: stringResource(R.string.pref_power_saving_off),
    stringResource(R.string.wear_settings_background_playback) + ": " +
        onOffWord(state.backgroundPlaybackEnabled)
).joinToString(", ")

@Composable
private fun labelFor(table: List<Pair<String, Int>>, value: String): String? =
    table.firstOrNull { it.first == value }?.let { stringResource(it.second) }

@Composable
private fun onOffWord(enabled: Boolean): String = stringResource(
    if (enabled) R.string.wear_companion_summary_on else R.string.wear_companion_summary_off
)

/**
 * The Screen subgroup's two view-mode rows.
 *
 * S2643: held apart to keep the group's body short enough for detekt. Only literals moved here - the
 * BackgroundModeControls and ColorSchemeControls calls stayed in the root function, because
 * assert-wear-settings-parity resolves a row to the FIRST invocation of the helper carrying its tag,
 * so moving a call site would slide those rows down the file and break the order it compares against
 * the watch menu map.
 */
@Composable
private fun ViewModeRows(state: WatchSettingsState, onChanged: () -> Unit) {
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

/** Whether the watch shows its Streams section - part of Media types, but not an allowed-type toggle. */
@Composable
private fun StreamsSectionSwitch(state: WatchSettingsState, onChanged: () -> Unit) {
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

/** The Slideshow subgroup's own toggle, held apart to keep the group's body flat. */
@Composable
private fun SlideshowSwitch(state: WatchSettingsState, onChanged: () -> Unit) {
    SwitchRow(
        tag = "wearSwitchSlideshow",
        label = stringResource(R.string.wear_settings_slideshow),
        description = stringResource(R.string.wear_settings_slideshow_desc),
        checked = state.slideshowEnabled
    ) {
        state.slideshowEnabled = it
        onChanged()
    }
}

/** The Screen subgroup's keep-awake toggle, held apart to keep the group's body flat. */
@Composable
private fun KeepAwakeSwitch(state: WatchSettingsState, onChanged: () -> Unit) {
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
    Spacer(Modifier.height(SPACING_SMALL))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelAutoHideChips(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    onSecondsSettled: () -> Unit
) {
    val label = stringResource(R.string.wear_settings_panel_auto_hide)
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        PANEL_AUTO_HIDE_INTERVALS.forEach { value ->
            val chipLabel = value.toString()
            FilterChip(
                selected = value == seconds,
                onClick = {
                    Timber.d("S2866: panel auto-hide chip selected value=$value")
                    onSecondsChange(value)
                    onSecondsSettled()
                },
                label = { Text(chipLabel) },
                // S2091: a chip's own label does not reach the accessibility node, so without this the
                // options dump as anonymous checkboxes and the screen reader announces none of them.
                modifier = Modifier
                    .testTag("wearPanelAutoHide_" + value)
                    .semantics { contentDescription = label + ": " + chipLabel }
            )
        }
    }
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
/**
 * S2522: the watch's colour scheme at its canonical Screen position, one chip per scheme.
 *
 * Each chip carries its own `contentDescription` for the reason the background chips beside it do
 * (S2091): a chip's label does not reach the accessibility node, so without it the eight options dump
 * as anonymous checkboxes and the screen reader announces none of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSchemeControls(viewModel: WearSyncViewModel) {
    val scheme by viewModel.colorScheme.collectAsState()

    Text(
        text = stringResource(R.string.wear_settings_color_scheme),
        style = MaterialTheme.typography.bodySmall
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL),
        verticalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        COLOR_SCHEMES.forEach { (value, labelRes) ->
            val chipLabel = stringResource(labelRes)
            FilterChip(
                selected = value == scheme,
                onClick = { viewModel.updateColorScheme(value) },
                label = { Text(chipLabel) },
                modifier = Modifier
                    .testTag("wearColorScheme_" + value)
                    .semantics { contentDescription = chipLabel }
            )
        }
    }
    Spacer(Modifier.height(SPACING_SMALL))
}

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

/** S2536: in PowerSavingTrigger declaration order, reusing the phone row's own option labels. */
private val POWER_SAVING_TRIGGERS = listOf(
    PowerSavingTrigger.OFF.name to R.string.pref_power_saving_off,
    PowerSavingTrigger.ALWAYS.name to R.string.pref_power_saving_always,
    PowerSavingTrigger.BELOW_10.name to R.string.pref_power_saving_below_10,
    PowerSavingTrigger.BELOW_15.name to R.string.pref_power_saving_below_15,
    PowerSavingTrigger.BELOW_20.name to R.string.pref_power_saving_below_20,
    PowerSavingTrigger.BELOW_30.name to R.string.pref_power_saving_below_30,
)
