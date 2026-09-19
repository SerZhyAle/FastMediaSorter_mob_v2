package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

// Wear Material keeps its own chip metrics internal, so the standard row restates them once here
// instead of letting every screen invent its own icon box (S3259).
private val CHIP_ICON_SIZE = 24.dp

/**
 * The standard Wear list row: one label, an optional subtitle and an optional icon, drawn with the
 * geometry and typography fixed by `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.1.
 *
 * Screens take this instead of a private chip wrapper so height, corner radius, icon slot and
 * caption scale stay identical across the module.
 */
@Composable
fun StandardWearChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        icon = icon?.let { iconSlot ->
            {
                Box(
                    modifier = Modifier
                        .size(CHIP_ICON_SIZE)
                        .wrapContentSize(Alignment.Center),
                    content = iconSlot
                )
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = secondaryLabel?.let { secondary ->
            {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/**
 * [StandardWearChip]'s signature over [LongPressChip], for rows that also answer a long press.
 *
 * The library `Chip` cannot serve both gestures from one row (S1953), so the long-press variant has
 * to be built on the project's own chip rather than on the Material one.
 */
@Composable
fun StandardWearLongPressChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    primary: Boolean = true
) {
    LongPressChip(
        onClick = onClick,
        onLongClick = onLongClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.fillMaxWidth(),
        icon = icon?.let { iconSlot ->
            {
                Box(
                    modifier = Modifier
                        .size(CHIP_ICON_SIZE)
                        .wrapContentSize(Alignment.Center),
                    content = iconSlot
                )
            }
        },
        secondaryLabel = secondaryLabel?.let { secondary ->
            {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors()
    )
}
