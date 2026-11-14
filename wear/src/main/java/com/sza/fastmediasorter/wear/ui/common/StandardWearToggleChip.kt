package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.sza.fastmediasorter.wear.ui.theme.WearAppTheme
import timber.log.Timber

// Wear Material keeps its own toggle metrics internal, so the standard toggle row restates the
// control size once here instead of letting every screen size its own glyph (S3260).
private val TOGGLE_ICON_SIZE = 24.dp
private const val NARROW_MAX_LINES = 2

// BOOLEAN-OPTION 2.1 section 2: the checkbox leads the row with an 8 dp gap to its caption.
private val CHECKBOX_CAPTION_GAP = 8.dp

// The toolkit always reserves its trailing control box, which stays empty on a checkbox row; the
// end padding is dropped so that empty box does not also take the chip's own end inset.
private val CHECKBOX_ROW_PADDING = PaddingValues(start = 14.dp, top = 6.dp, end = 0.dp, bottom = 6.dp)

/**
 * The standard Wear toggle row: one label and a checkbox or radio glyph, drawn with the geometry and
 * typography fixed by `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.1.
 *
 * Built on the library [ToggleChip] so focus scaling, touch geometry and toggle semantics come from
 * Wear Material rather than from a hand-rolled row; the dual-tone state glyph is the project's own
 * (S2468), which is why the glyph is drawn here instead of taken from the defaults.
 *
 * A boolean row (`radio = false`) follows the `BOOLEAN-OPTION` checkbox pattern: the square checkbox
 * sits at reading start in the label slot, top-aligned with the caption's first line, and the row is
 * exposed as one checkbox. The toolkit places its toggle control at the trailing end, which the
 * contract refuses, so that slot stays empty. A radio row is a single choice outside the contract and
 * keeps the toolkit's trailing radio glyph.
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
    val glyph = if (radio) {
        ToggleChipDefaults.radioIcon(checked)
    } else {
        ToggleChipDefaults.checkboxIcon(checked)
    }
    val tint = if (checked) {
        WearAppTheme.colors.toggleOn
    } else {
        WearAppTheme.colors.toggleOff
    }
    // The toolkit leaves the role to its toggle control, and the glyph here is a plain icon, so the
    // boolean row names its checkbox role itself. Two settings can offer the same three mode names,
    // so a caller that needs the group spoken replaces the description without clearing the chip's
    // own toggle action.
    val described = modifier.semantics {
        if (!radio) {
            role = Role.Checkbox
        }
        if (accessibilityLabel != label) {
            contentDescription = accessibilityLabel
        }
    }

    ToggleChip(
        checked = checked,
        onCheckedChange = { value ->
            Timber.d("S4627: toggle row tapped label=$label radio=$radio checked=$value")
            onCheckedChange(value)
        },
        modifier = described
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        label = {
            if (!radio) {
                StateGlyph(glyph, tint, Modifier.align(Alignment.Top))
                Spacer(Modifier.width(CHECKBOX_CAPTION_GAP))
            }
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
            if (radio) {
                StateGlyph(glyph, tint)
            }
        },
        colors = ToggleChipDefaults.toggleChipColors(),
        contentPadding = if (radio) ToggleChipDefaults.ContentPadding else CHECKBOX_ROW_PADDING
    )
}

@Composable
private fun StateGlyph(glyph: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = glyph,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(TOGGLE_ICON_SIZE)
    )
}
