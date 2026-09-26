package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.model.WearFaceSlotOptionGroup

private val FACE_SLOT_ROW_CHEVRON_SIZE = 24.dp
private val FACE_SLOT_OPTION_MIN_HEIGHT = 48.dp

/**
 * S3558: the four watch face buttons, each pointed at a watch section, a watch program, a piece of app
 * data or a system value.
 *
 * A picker dialog rather than the dropdown [WearCompanionSelectorRow] opens: the list is thirty-one
 * options in five families, and a dropdown has no headings to keep them apart. The dialog closes on a
 * pick, so it carries a single cancel and no confirm - the selection-dialog exemption of the dialog
 * action pair.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(slot.labelRes())) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WearFaceSlotOptionGroup.entries.forEach { group ->
                    Text(
                        text = stringResource(group.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = SPACING_CARD, bottom = SPACING_TINY)
                    )
                    WearFaceSlotOption.entries.filter { it.group == group }.forEach { option ->
                        FaceSlotOptionRow(option = option, selected = option == selected, onPick = onPick)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("wearFaceSlotCancel")) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FaceSlotOptionRow(
    option: WearFaceSlotOption,
    selected: Boolean,
    onPick: (WearFaceSlotOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FACE_SLOT_OPTION_MIN_HEIGHT)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(option) })
            .testTag("wearFaceSlotOption_${option.name}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Null: the whole row is the selectable node, so the radio must not be a second target.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(option.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = SPACING_SMALL)
        )
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
