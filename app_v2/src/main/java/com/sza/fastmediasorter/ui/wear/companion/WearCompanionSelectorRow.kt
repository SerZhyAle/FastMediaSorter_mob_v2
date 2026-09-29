package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sza.fastmediasorter.R

private val SELECTOR_ROW_SPACING = 8.dp
private val SELECTOR_ROW_CHEVRON_SIZE = 24.dp
private val SELECTOR_ROW_VALUE_GAP = 6.dp
private val SELECTOR_ROW_VALUE_TEXT_SIZE = 16.sp

/**
 * Optional help content for a group header: the title and message shown by TooltipDialog.
 */
data class CompanionGroupHelp(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

/**
 * Optional help content for a toggle row: the title and message shown by TooltipDialog.
 */
data class CompanionToggleHelp(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

/**
 * Optional header configuration for a companion group: a leading icon and/or help button.
 */
data class CompanionGroupHeader(
    @DrawableRes val iconRes: Int? = null,
    val help: CompanionGroupHelp? = null
)

/**
 * Configuration for the optional "enter custom value" menu entry (used by slideshow length).
 */
data class CustomValueEntry(
    val entryLabel: String,
    val onConfirm: (String) -> Unit
)

/**
 * Single-row selector mirroring the phone Settings dropdown row's valueAsText mode: caption, current
 * value and chevron share one line, and the whole row is the tap target that opens a dropdown list of
 * preset options; selecting one calls back. When [customValueEntry] is supplied, the menu also offers
 * an item that reveals an inline numeric field.
 *
 * Caption and value stay adjacent (S2328, owner ruling 2026-09-26): the title hugs its text, the value
 * follows it at value size in the accent color, and the chevron sits right after the value instead of
 * at the row's far edge - the former stacked two-line layout read as a second caption. Pure
 * presentation: the caller owns the setting state.
 */
@Composable
fun WearCompanionSelectorRow(
    title: String,
    value: String,
    entries: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    tag: String,
    customValueEntry: CustomValueEntry? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var customMode by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = SELECTOR_ROW_SPACING)) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .testTag(tag)
                    .padding(vertical = SELECTOR_ROW_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = SELECTOR_ROW_VALUE_TEXT_SIZE),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = SELECTOR_ROW_VALUE_GAP)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(SELECTOR_ROW_CHEVRON_SIZE)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                entries.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelected(key)
                            expanded = false
                        }
                    )
                }
                if (customValueEntry != null) {
                    DropdownMenuItem(
                        text = { Text(customValueEntry.entryLabel) },
                        onClick = {
                            expanded = false
                            customMode = true
                        }
                    )
                }
            }
        }
        if (customMode && customValueEntry != null) {
            CustomValueInput(
                customText = customText,
                onTextChange = { customText = it },
                onConfirm = {
                    customValueEntry.onConfirm(customText)
                    customMode = false
                    customText = ""
                }
            )
        }
    }
}

@Composable
private fun CustomValueInput(
    customText: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SELECTOR_ROW_SPACING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = customText,
            onValueChange = onTextChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(SELECTOR_ROW_SPACING))
        IconButton(onClick = onConfirm) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.apply),
                modifier = Modifier.size(SELECTOR_ROW_CHEVRON_SIZE)
            )
        }
    }
}
