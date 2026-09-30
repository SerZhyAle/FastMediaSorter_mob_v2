package com.sza.fastmediasorter.ui.wear.companion

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.model.WearFaceSlotOptionGroup

private val FACE_SLOT_ROW_CHEVRON_SIZE = 24.dp
private val FACE_SLOT_OPTION_MIN_HEIGHT = 48.dp
private const val DIALOG_WIDTH_FRACTION_PORTRAIT = 0.92f
private const val DIALOG_WIDTH_FRACTION_LANDSCAPE = 0.75f
private const val DIALOG_MAX_HEIGHT_FRACTION = 0.85f

/**
 * S3558: the four watch face buttons, each pointed at a watch section, a watch program, a piece of app
 * data or a system value.
 *
 * A picker dialog matching the gesture-action picker style: sectioned entries, vector icons, trailing
 * checkmarks and highlight on active choice, responsive sizing in portrait/landscape, and sticky header
 * with current selection preview.
 */
@Composable
fun WearFaceSlotsGroup(viewModel: WearFaceSlotsViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val assignment by viewModel.assignment.collectAsState()
    var pickingSlot by rememberSaveable { mutableStateOf<WearFaceSlot?>(null) }

    WearCompanionGroup(
        title = stringResource(R.string.wear_face_slot_group_title),
        summary = null,
        expanded = expanded,
        tag = "wearGroupFaceSlots",
        onExpandedChange = { expanded = it }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            WearFaceSlot.entries.forEach { slot ->
                FaceSlotRow(
                    slot = slot,
                    option = assignment.optionFor(slot),
                    onClick = { pickingSlot = slot }
                )
            }
        }
    }

    pickingSlot?.let { slot ->
        FaceSlotPickerDialog(
            slot = slot,
            selected = assignment.optionFor(slot),
            onPick = { option ->
                viewModel.select(slot, option)
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null }
        )
    }
}

@Composable
private fun FaceSlotRow(slot: WearFaceSlot, option: WearFaceSlotOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("wearFaceSlot${slot.ordinal + 1}")
            .focusable()
            .padding(vertical = SPACING_SMALL),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(option.iconRes()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = SPACING_SMALL)
                .size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(slot.labelRes()), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(option.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(FACE_SLOT_ROW_CHEVRON_SIZE)
        )
    }
}

@Composable
private fun FaceSlotPickerDialog(
    slot: WearFaceSlot,
    selected: WearFaceSlotOption,
    onPick: (WearFaceSlotOption) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthFraction = if (isLandscape) DIALOG_WIDTH_FRACTION_LANDSCAPE else DIALOG_WIDTH_FRACTION_PORTRAIT

    val listState = rememberLazyListState()

    LaunchedEffect(selected) {
        var targetIndex = 0
        for (group in WearFaceSlotOptionGroup.entries) {
            targetIndex++
            val options = WearFaceSlotOption.entries.filter { it.group == group }
            val optIndex = options.indexOf(selected)
            if (optIndex >= 0) {
                targetIndex += optIndex
                listState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
                break
            }
            targetIndex += options.size
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .fillMaxHeight(DIALOG_MAX_HEIGHT_FRACTION),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(SPACING_CARD)
            ) {
                FaceSlotPickerHeader(slot = slot, selected = selected)
                FaceSlotPickerList(
                    listState = listState,
                    selected = selected,
                    onPick = onPick,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SPACING_TINY),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("wearFaceSlotCancel")
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceSlotPickerHeader(slot: WearFaceSlot, selected: WearFaceSlotOption) {
    Text(
        text = stringResource(slot.labelRes()),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = SPACING_TINY)
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SPACING_SMALL)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SPACING_SMALL, vertical = SPACING_TINY),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(selected.iconRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(selected.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SPACING_TINY)
            )
        }
    }
}

@Composable
private fun FaceSlotPickerList(
    listState: LazyListState,
    selected: WearFaceSlotOption,
    onPick: (WearFaceSlotOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth()
    ) {
        WearFaceSlotOptionGroup.entries.forEach { group ->
            val groupOptions = WearFaceSlotOption.entries.filter { it.group == group }
            if (groupOptions.isNotEmpty()) {
                item(key = "header_${group.name}") {
                    Text(
                        text = stringResource(group.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = SPACING_SMALL, bottom = SPACING_TINY)
                    )
                }
                items(groupOptions.size, key = { groupOptions[it].name }) { index ->
                    val option = groupOptions[index]
                    FaceSlotOptionRow(
                        option = option,
                        selected = option == selected,
                        onPick = onPick
                    )
                }
            }
        }
    }
}

@Composable
private fun FaceSlotOptionRow(
    option: WearFaceSlotOption,
    selected: Boolean,
    onPick: (WearFaceSlotOption) -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FACE_SLOT_OPTION_MIN_HEIGHT)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(role = Role.RadioButton, onClick = { onPick(option) })
            .focusable()
            .padding(horizontal = SPACING_SMALL, vertical = SPACING_TINY)
            .testTag("wearFaceSlotOption_${option.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(option.iconRes()),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(option.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SPACING_SMALL)
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@StringRes
private fun WearFaceSlot.labelRes(): Int = when (this) {
    WearFaceSlot.OUTER_LEFT -> R.string.wear_face_slot_outer_left
    WearFaceSlot.INNER_LEFT -> R.string.wear_face_slot_inner_left
    WearFaceSlot.INNER_RIGHT -> R.string.wear_face_slot_inner_right
    WearFaceSlot.OUTER_RIGHT -> R.string.wear_face_slot_outer_right
}

@StringRes
private fun WearFaceSlotOptionGroup.labelRes(): Int = when (this) {
    WearFaceSlotOptionGroup.WATCH_SECTIONS -> R.string.wear_face_slot_section_watch_sections
    WearFaceSlotOptionGroup.WATCH_PROGRAMS -> R.string.wear_face_slot_section_watch_programs
    WearFaceSlotOptionGroup.APP_DATA -> R.string.wear_face_slot_section_app_data
    WearFaceSlotOptionGroup.SYSTEM -> R.string.wear_face_slot_section_system
    WearFaceSlotOptionGroup.NONE -> R.string.wear_face_slot_section_none
}

@Suppress("CyclomaticComplexMethod")
@DrawableRes
private fun WearFaceSlotOption.iconRes(): Int = when (this) {
    WearFaceSlotOption.DEST_RESOURCES -> R.drawable.ic_folder
    WearFaceSlotOption.DEST_PHONE -> R.drawable.ic_profile_personal_smartphone
    WearFaceSlotOption.DEST_LOCAL -> R.drawable.ic_watch
    WearFaceSlotOption.DEST_STREAMS -> R.drawable.ic_stream
    WearFaceSlotOption.DEST_APPS -> R.drawable.ic_apps
    WearFaceSlotOption.DEST_FAVOURITES -> R.drawable.ic_star_filled
    WearFaceSlotOption.DEST_PHONE_CAMERA -> R.drawable.ic_camera_capture
    WearFaceSlotOption.DEST_HOME -> R.drawable.ic_launcher_mode
    WearFaceSlotOption.DEST_CALCULATOR -> R.drawable.ic_calculator
    WearFaceSlotOption.DEST_NETWORK_MONITOR -> R.drawable.ic_network_monitor
    WearFaceSlotOption.DEST_GAME -> R.drawable.ic_game_kryvavitsa
    WearFaceSlotOption.DEST_VOICE_RECORDER -> R.drawable.ic_microphone
    WearFaceSlotOption.DEST_SYSTEM_INFO -> R.drawable.ic_info
    WearFaceSlotOption.DEST_WATER_FLASHLIGHT -> R.drawable.ic_water_flashlight
    WearFaceSlotOption.DEST_MOTION_MONITOR -> R.drawable.ic_steps
    WearFaceSlotOption.DEST_BODY_SENSOR -> R.drawable.ic_favorite
    WearFaceSlotOption.DEST_BLOOD_PRESSURE -> R.drawable.ic_favorite
    WearFaceSlotOption.DEST_BROADCAST -> R.drawable.ic_live_broadcast
    WearFaceSlotOption.DEST_STOPWATCH -> R.drawable.ic_stopwatch
    WearFaceSlotOption.DEST_TOURIST -> R.drawable.ic_tourist
    WearFaceSlotOption.DEST_CLIPBOARD -> R.drawable.ic_copy
    WearFaceSlotOption.DEST_SOS -> R.drawable.ic_sos
    WearFaceSlotOption.DATA_FAVOURITES_COUNT -> R.drawable.ic_star_filled
    WearFaceSlotOption.DATA_LAST_RESOURCE -> R.drawable.ic_history
    WearFaceSlotOption.DATA_NOW_PLAYING -> R.drawable.ic_gesture_action_play_pause
    WearFaceSlotOption.SYS_BATTERY -> R.drawable.ic_battery
    WearFaceSlotOption.SYS_DATE -> R.drawable.ic_gesture_action_calendar
    WearFaceSlotOption.SYS_NEXT_ALARM -> R.drawable.ic_gesture_action_alarm
    WearFaceSlotOption.SYS_ALARMS -> R.drawable.ic_gesture_action_alarm
    WearFaceSlotOption.SYS_TIMER -> R.drawable.ic_schedule
    WearFaceSlotOption.NONE -> R.drawable.ic_gesture_action_none
}

// An exhaustive when rather than a map: a constant added to the enum without a label fails the build
// here instead of showing a blank row.
@Suppress("CyclomaticComplexMethod")
@StringRes
private fun WearFaceSlotOption.labelRes(): Int = when (this) {
    WearFaceSlotOption.DEST_RESOURCES -> R.string.wear_face_slot_opt_resources
    WearFaceSlotOption.DEST_PHONE -> R.string.wear_face_slot_opt_phone
    WearFaceSlotOption.DEST_LOCAL -> R.string.wear_face_slot_opt_local
    WearFaceSlotOption.DEST_STREAMS -> R.string.wear_face_slot_opt_streams
    WearFaceSlotOption.DEST_APPS -> R.string.wear_face_slot_opt_apps
    WearFaceSlotOption.DEST_FAVOURITES -> R.string.wear_face_slot_opt_favourites
    WearFaceSlotOption.DEST_PHONE_CAMERA -> R.string.wear_face_slot_opt_phone_camera
    WearFaceSlotOption.DEST_HOME -> R.string.wear_face_slot_opt_home
    WearFaceSlotOption.DEST_CALCULATOR -> R.string.wear_face_slot_opt_calculator
    WearFaceSlotOption.DEST_NETWORK_MONITOR -> R.string.wear_face_slot_opt_network_monitor
    WearFaceSlotOption.DEST_GAME -> R.string.wear_face_slot_opt_game
    WearFaceSlotOption.DEST_VOICE_RECORDER -> R.string.wear_face_slot_opt_voice_recorder
    WearFaceSlotOption.DEST_SYSTEM_INFO -> R.string.wear_face_slot_opt_system_info
    WearFaceSlotOption.DEST_WATER_FLASHLIGHT -> R.string.wear_face_slot_opt_water_flashlight
    WearFaceSlotOption.DEST_MOTION_MONITOR -> R.string.wear_face_slot_opt_motion_monitor
    WearFaceSlotOption.DEST_BODY_SENSOR -> R.string.wear_face_slot_opt_body_sensor
    WearFaceSlotOption.DEST_BLOOD_PRESSURE -> R.string.wear_face_slot_opt_blood_pressure
    WearFaceSlotOption.DEST_BROADCAST -> R.string.wear_face_slot_opt_broadcast
    WearFaceSlotOption.DEST_STOPWATCH -> R.string.wear_face_slot_opt_stopwatch
    WearFaceSlotOption.DEST_TOURIST -> R.string.wear_face_slot_opt_tourist
    WearFaceSlotOption.DEST_CLIPBOARD -> R.string.wear_face_slot_opt_clipboard
    WearFaceSlotOption.DEST_SOS -> R.string.wear_face_slot_opt_sos
    WearFaceSlotOption.DATA_FAVOURITES_COUNT -> R.string.wear_face_slot_opt_favourites_count
    WearFaceSlotOption.DATA_LAST_RESOURCE -> R.string.wear_face_slot_opt_last_resource
    WearFaceSlotOption.DATA_NOW_PLAYING -> R.string.wear_face_slot_opt_now_playing
    WearFaceSlotOption.SYS_BATTERY -> R.string.wear_face_slot_opt_battery
    WearFaceSlotOption.SYS_DATE -> R.string.wear_face_slot_opt_date
    WearFaceSlotOption.SYS_NEXT_ALARM -> R.string.wear_face_slot_opt_next_alarm
    WearFaceSlotOption.SYS_ALARMS -> R.string.wear_face_slot_opt_alarms
    WearFaceSlotOption.SYS_TIMER -> R.string.wear_face_slot_opt_timer
    WearFaceSlotOption.NONE -> R.string.wear_face_slot_opt_none
}
