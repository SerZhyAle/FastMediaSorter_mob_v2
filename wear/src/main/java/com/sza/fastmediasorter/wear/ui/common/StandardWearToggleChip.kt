package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme

// Wear Material keeps its own toggle metrics internal, so the standard toggle row restates the
// control size once here instead of letting every screen size its own glyph (S3260).
private val TOGGLE_ICON_SIZE = 24.dp
private const val NARROW_MAX_LINES = 2

/**
 * The standard Wear toggle row: one label and a switch or radio glyph, drawn with the geometry and
 * typography fixed by `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.1.
 *
 * Built on the library [ToggleChip] so focus scaling, touch geometry and toggle semantics come from
 * Wear Material rather than from a hand-rolled row; the dual-tone state glyph is the project's own
 * (S2468), which is why the control slot is filled here instead of taken from the defaults.
 *
 * No `secondaryLabel` or `enabled` slot: detekt refuses the eighth parameter and no settings row in
 * the module asks for either, so both stay at the library default until one does.
 */
@Composable
fun StandardWearToggleChip(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    radio: Boolean = false,
    accessibilityLabel: String = label,
    narrow: Boolean = false
) {
    val toggleIcon = if (radio) {
        ToggleChipDefaults.radioIcon(checked)
    } else {
        ToggleChipDefaults.switchIcon(checked)
    }
    val tint = if (checked) {
        WearAppTheme.colors.toggleOn
    } else {
        WearAppTheme.colors.toggleOff
    }
    // Two settings can offer the same three mode names, so a caller that needs the group spoken
    // replaces the description without clearing the chip's own toggle role and action.
    val described = if (accessibilityLabel == label) {
        modifier
    } else {
        modifier.semantics { contentDescription = accessibilityLabel }
    }

    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = described
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                // S3362: a full-width row wraps as far as its label needs. One line and an ellipsis cut
                // "Keep screen on" and "Disable animations" on the 192 dp review emulator at font scale
                // 1.0, which WO-V1 fails as truncated essential text. A narrow cell keeps its two-line cap,
                // because it has no width to grow into.
                maxLines = if (narrow) NARROW_MAX_LINES else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis
            )
        },
        toggleControl = {
            Icon(
                imageVector = toggleIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(TOGGLE_ICON_SIZE)
            )
        },
        colors = ToggleChipDefaults.toggleChipColors()
    )
}
