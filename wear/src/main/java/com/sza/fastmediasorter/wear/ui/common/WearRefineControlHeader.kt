package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.util.GridColumnFit

/**
 * S2136: the height this header occupies, which a screen adds to its list's top padding.
 *
 * Public and derived from the constant [GridColumnFit] already drops a column to protect, rather
 * than written as a number: the streams screen's equivalent is private to that file, and a second
 * literal is how a toolbar and the list beneath it start disagreeing about one height.
 */
val WearRefineHeaderHeight: Dp = GridColumnFit.DEFAULT_MIN_TARGET_DP.dp

private val REFINE_ICON_SIZE = 20.dp
private val REFINE_ROW_PADDING = 4.dp

/**
 * S2473: the drawn square, two thirds of the 48dp the button is laid out and tapped at.
 *
 * The button keeps [WearRefineHeaderHeight] so its tap target does not shrink with its face - the
 * overlay had to stop covering the list, not stop being hittable.
 */
private val REFINE_BUTTON_FACE = 32.dp
private val REFINE_STROKE_IDLE = 1.dp
private val REFINE_STROKE_ACTIVE = 2.dp
private const val REFINE_ACTIVE_FILL_ALPHA = 0.24f

/**
 * S2473: which of the two controls is currently doing something.
 *
 * [refineActive] covers sorting and filtering together, because one button now stands for both -
 * a wearer who filtered by type and a wearer who reordered the list have both refined it.
 */
data class WearRefineHeaderState(
    val searchActive: Boolean,
    val refineActive: Boolean
)

/** S2136: what each icon is called when it is read aloud. */
data class WearRefineHeaderLabels(
    val search: String,
    val refine: String
)

/** S2136: what a tap on each icon does. */
data class WearRefineHeaderActions(
    val onSearchClick: () -> Unit,
    val onRefineClick: () -> Unit
)

/**
 * S2136: the search and refine icons sitting over a content list.
 *
 * Knows nothing about what it narrows - a state, two labels and two callbacks - so one
 * implementation serves every content route on the watch instead of each screen growing its own.
 * The caller lays it over the list rather than inside it (strategic 5.3), which is what keeps it on
 * screen when a query has emptied the list and the only way back is to clear that query.
 *
 * The carriers are kept although two controls would now fit flat: the caller's own call site reads
 * as state, names and behaviour, and S2473 removed a control rather than settling the shape.
 */
@Composable
fun WearRefineControlHeader(
    state: WearRefineHeaderState,
    labels: WearRefineHeaderLabels,
    actions: WearRefineHeaderActions,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = REFINE_ROW_PADDING),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RefineIconButton(
            active = state.searchActive,
            onClick = actions.onSearchClick,
            icon = Icons.Filled.Search,
            description = labels.search
        )

        // S2473: one button for sorting and filtering both. The filter used to be a third icon that
        // appeared only on a mixed list, which made its rule unreadable from the screen - the rule
        // is unchanged, but it is now a sentence inside the menu rather than a missing button.
        RefineIconButton(
            active = state.refineActive,
            onClick = actions.onRefineClick,
            iconRes = R.drawable.ic_sort,
            description = labels.refine
        )
    }
}

/**
 * S2473: one overlay control - a small outlined square over the list, not a plate on top of it.
 *
 * The plate is dropped by handing the shared button a transparent background rather than by editing
 * that button: the settings screen and the choice dialogs draw from the same primitive and have no
 * reason to change. What is drawn instead lives in the content slot, so the 48dp the button is laid
 * out and tapped at is untouched.
 */
@Composable
private fun RefineIconButton(
    active: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    description: String
) {
    val faceColor = if (active) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
    RectangularButton(
        onClick = onClick,
        modifier = Modifier.size(WearRefineHeaderHeight),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Transparent,
            contentColor = faceColor
        )
    ) {
        RefineButtonFace(active = active, faceColor = faceColor) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    modifier = Modifier.size(REFINE_ICON_SIZE)
                )
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = description,
                    modifier = Modifier.size(REFINE_ICON_SIZE)
                )
            }
        }
    }
}

/**
 * The outline, and the faint fill that marks an active control.
 *
 * A thicker stroke and a `selected` flag carry the active state beside the tint, because a colour
 * difference alone is not a difference on a watch face in sunlight and says nothing aloud.
 */
@Composable
private fun RefineButtonFace(
    active: Boolean,
    faceColor: Color,
    content: @Composable BoxScope.() -> Unit
) {
    val stroke = if (active) REFINE_STROKE_ACTIVE else REFINE_STROKE_IDLE
    val fill = if (active) faceColor.copy(alpha = REFINE_ACTIVE_FILL_ALPHA) else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(REFINE_BUTTON_FACE)
            .clip(WearCellShape)
            .background(color = fill, shape = WearCellShape)
            .border(width = stroke, color = faceColor, shape = WearCellShape)
            .semantics { selected = active },
        content = content
    )
}
