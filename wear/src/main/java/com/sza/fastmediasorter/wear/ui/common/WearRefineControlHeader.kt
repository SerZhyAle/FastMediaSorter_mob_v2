package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
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
 * S2136: which of the three controls are currently doing something, and whether to offer the filter.
 *
 * [showFilter] is a parameter rather than a decision inside the header because ADR-2 makes the
 * filter icon a property of the list, not of the route that opened it.
 */
data class WearRefineHeaderState(
    val searchActive: Boolean,
    val filterActive: Boolean,
    val sortActive: Boolean,
    val showFilter: Boolean
)

/** S2136: what each icon is called when it is read aloud. */
data class WearRefineHeaderLabels(
    val search: String,
    val filter: String,
    val sort: String
)

/** S2136: what a tap on each icon does. */
data class WearRefineHeaderActions(
    val onSearchClick: () -> Unit,
    val onFilterClick: () -> Unit,
    val onSortClick: () -> Unit
)

/**
 * S2136: the search, filter and sort icons sitting over a content list.
 *
 * Knows nothing about what it narrows - a state, three labels and three callbacks - so one
 * implementation serves every content route on the watch instead of each screen growing its own.
 * The caller lays it over the list rather than inside it (strategic 5.3), which is what keeps it on
 * screen when a query has emptied the list and the only way back is to clear that query.
 *
 * The three carriers exist because the flat form is eleven values and `LongParameterList` caps a
 * function at eight - the same reason the streams screen next door carries `StreamsControlState`
 * and `StreamsActions`.
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

        // ADR-2: a homogeneous list gets no filter icon at all rather than a disabled one - the
        // dialog behind it would offer the single type the list already shows.
        if (state.showFilter) {
            RefineIconButton(
                active = state.filterActive,
                onClick = actions.onFilterClick,
                icon = Icons.Filled.FilterList,
                description = labels.filter
            )
        }

        RefineIconButton(
            active = state.sortActive,
            onClick = actions.onSortClick,
            icon = Icons.AutoMirrored.Filled.Sort,
            description = labels.sort
        )
    }
}

@Composable
private fun RefineIconButton(
    active: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    description: String
) {
    RectangularButton(
        onClick = onClick,
        modifier = Modifier.size(WearRefineHeaderHeight),
        colors = if (active) {
            ButtonDefaults.primaryButtonColors()
        } else {
            ButtonDefaults.secondaryButtonColors()
        }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(REFINE_ICON_SIZE)
        )
    }
}
