package com.sza.fastmediasorter.wear.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import timber.log.Timber

private val MENU_TITLE_GAP = 8.dp
private val MENU_GROUP_GAP = 10.dp
private val MENU_ICON_SIZE = 20.dp

/** Below this there is nothing to choose between, so the filter group is a sentence instead. */
private const val MENU_MIN_FILTERABLE_TYPES = 2

/** S2473: what the refine menu currently shows as chosen, and what it may offer. */
data class WearRefineMenuState(
    val sortOptions: List<BrowseSortOrder>,
    val sortSelected: BrowseSortOrder,
    val filterOptions: List<WearContentType>,
    val filterSelected: WearContentType?,
    val searchQuery: String
)

/** S2473: what a choice on the refine menu does. A null type means every type. */
data class WearRefineMenuActions(
    val onSortSelected: (BrowseSortOrder) -> Unit,
    val onFilterSelected: (WearContentType?) -> Unit,
    val onClearSearch: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * S2473: every way of refining a list, on one screen, in one readable column.
 *
 * It replaces two separate choice dialogs, and the two are merged rather than shrunk because a
 * filter control that appears on some lists and not on others is a rule the wearer cannot read off
 * the screen - here the same rule becomes a sentence in the place he already went looking.
 *
 * The one property it refuses to take is the caller's [WearViewMode]. The old sort dialog accepted
 * it and was handed the FILE LIST's mode, so a wearer browsing in tiles got a three-column menu
 * where a sort order was cut to three letters. Column count is a property of this menu, not of the
 * list behind it (strategic ADR-2), which is what `fixedEnumeration = false` states to
 * [wearChoiceRows].
 *
 * Two carriers rather than nine parameters, following the neighbouring refine header - `detekt`
 * caps a function at eight, and the grouping is the same one the header already uses.
 */
@Composable
fun WearRefineMenuScreen(
    state: WearRefineMenuState,
    actions: WearRefineMenuActions
) {
    Dialog(
        showDialog = true,
        onDismissRequest = actions.onDismiss
    ) {
        val listState = rememberWearListState()
        val filterColors = ChipDefaults.childChipColors()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthDp = maxWidth.value.toInt()
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_refine_menu_title),
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier.padding(bottom = MENU_TITLE_GAP),
                        textAlign = TextAlign.Center
                    )
                }

                clearSearchRow(state = state, actions = actions)
                sortGroup(state = state, actions = actions, widthDp = widthDp)
                filterGroup(
                    state = state,
                    actions = actions,
                    widthDp = widthDp,
                    unselectedColors = filterColors
                )
            }
        }
    }
}

/**
 * The only place an active query can be dropped now that the search icon goes straight to typing.
 *
 * Offered only while a query is active, and labelled with the query itself: on a list narrowed
 * hours ago, "clear search" alone does not say what is being cleared.
 */
private fun ScalingLazyListScope.clearSearchRow(
    state: WearRefineMenuState,
    actions: WearRefineMenuActions
) {
    if (state.searchQuery.isBlank()) return
    item {
        Chip(
            onClick = {
                actions.onClearSearch()
                actions.onDismiss()
            },
            label = {
                Text(text = stringResource(R.string.wear_refine_menu_clear_search, state.searchQuery))
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(MENU_ICON_SIZE)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.primaryChipColors()
        )
    }
}

private fun ScalingLazyListScope.sortGroup(
    state: WearRefineMenuState,
    actions: WearRefineMenuActions,
    widthDp: Int
) {
    item { MenuGroupHeader(textRes = R.string.wear_refine_menu_sort_group) }
    wearChoiceRows(
        options = state.sortOptions,
        selected = state.sortSelected,
        labelOf = { stringResource(labelForSortOrder(it)) },
        onSelected = { order ->
            Timber.d("S2473: sort order picked on the refine menu")
            actions.onSortSelected(order)
            actions.onDismiss()
        },
        gridFit = oneColumnFit(widthDp)
    )
}

/**
 * The types this list actually holds, or the reason there is nothing to pick.
 *
 * The availability rule is unchanged - one content type means one possible answer - but it is now
 * said rather than expressed by a missing button (strategic ADR-3).
 */
private fun ScalingLazyListScope.filterGroup(
    state: WearRefineMenuState,
    actions: WearRefineMenuActions,
    widthDp: Int,
    // Resolved by the caller: chip colours read the theme, so they can only be built where
    // composition is running, and this builder is a list scope rather than a composable.
    unselectedColors: ChipColors
) {
    item { MenuGroupHeader(textRes = R.string.wear_refine_menu_filter_group) }
    if (state.filterOptions.size < MENU_MIN_FILTERABLE_TYPES) {
        item {
            Text(
                text = stringResource(R.string.wear_refine_menu_filter_single_type),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    val allTypes: List<WearContentType?> = listOf(null) + state.filterOptions
    wearChoiceRows(
        options = allTypes,
        selected = state.filterSelected,
        labelOf = { type ->
            if (type == null) {
                stringResource(R.string.wear_browse_filter_type_all)
            } else {
                stringResource(labelForContentType(type))
            }
        },
        onSelected = { type ->
            actions.onFilterSelected(type)
            actions.onDismiss()
        },
        gridFit = oneColumnFit(widthDp),
        unselectedColors = unselectedColors
    )
}

/**
 * One column, whatever the wearer's file list is set to.
 *
 * `LIST` is passed for completeness only - `fixedEnumeration = false` already pins the count - so
 * no caller's view mode can reach this menu even by being added as a parameter later.
 */
private fun oneColumnFit(widthDp: Int): WearChoiceGridFit = WearChoiceGridFit(
    viewMode = WearViewMode.LIST,
    availableWidthDp = widthDp,
    fixedEnumeration = false
)

@Composable
private fun MenuGroupHeader(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MENU_GROUP_GAP, bottom = MENU_TITLE_GAP)
    )
}
