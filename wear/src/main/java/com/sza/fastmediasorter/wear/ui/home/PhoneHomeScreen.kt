package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearCategoryOrigin
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.BrowseCategoryPresentation
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearListMetrics
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.wear.ui.settings.allowedContentTypes
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val TITLE_VERTICAL_PADDING = 12.dp

/**
 * The phone's virtual resources, reached from the watch.
 *
 * Registered sources that live on the phone are deliberately absent: they belong to the Resources
 * section, which is the whole point of splitting these two by content origin rather than media type.
 * The last row keeps the paired-phone folder browser reachable - it is a separate capability that
 * only ever had its entrance on the home screen.
 *
 * S2130: the row of categories is whatever `BrowseCategoryCatalog` returns for the phone origin. This
 * screen used to declare its own list, and being the set the owner called correct made it the one
 * every other screen was measured against while nothing kept them equal.
 */
@Composable
fun PhoneHomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberWearListState()

    // S2130 ADR-6: the type toggles narrow this screen too. The setting's label promises which types
    // are allowed without qualifying by origin, so an origin they cannot reach makes the promise false.
    val vocabulary = BrowseCategoryCatalog.categoriesFor(
        WearCategoryOrigin.PHONE,
        settings.allowedContentTypes()
    )
    val categories = vocabulary.filterNot { it.token == BrowseCategoryCatalog.TOKEN_BROWSE }
    val folderBrowser = vocabulary.firstOrNull { it.token == BrowseCategoryCatalog.TOKEN_BROWSE }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        if (categories.isEmpty()) {
            // S2130: now that the toggles reach this screen, settings is the only way to empty it -
            // the phone presents every category otherwise - so the cause can finally be named. No
            // retry, for the reason the two sibling screens give: the settings read succeeded.
            WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = stringResource(R.string.wear_media_types_all_disabled),
                onBack = { navController.popBackStack() }
            )
            return@WearScreenScaffold
        }

        // Same rule as the home screen: the mode is a request and the measured width is the answer.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(settings.viewMode, maxWidth.value.toInt())
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_section_phone),
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                if (columns == SINGLE_COLUMN) {
                    items(categories) { category ->
                        PhoneCategoryChip(
                            category = category,
                            onClick = {
                                navController.navigate(WearRoutes.browsePhone(category.token))
                            }
                        )
                    }
                } else {
                    items(categories.chunked(columns)) { rowCategories ->
                        PhoneCategoryRow(
                            categories = rowCategories,
                            columns = columns,
                            onCategoryClick = { category ->
                                navController.navigate(WearRoutes.browsePhone(category.token))
                            }
                        )
                    }
                }

                if (folderBrowser != null) {
                    item {
                        PhoneFolderChip(
                            category = folderBrowser,
                            onClick = { navController.navigate(WearRoutes.PHONE_RESOURCE) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A full-width row of its own in every mode: the paired-phone folder browser is a separate
 * capability, and a cell in the type grid would read as a seventh content category.
 *
 * It is also the one entry whose route carries no media type, which is what distinguishes it from
 * the flat All listing that sits directly above it.
 */
@Composable
private fun PhoneFolderChip(
    category: WearBrowseCategory,
    onClick: () -> Unit
) {
    val label = stringResource(BrowseCategoryPresentation.labelFor(category))
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(BrowseCategoryPresentation.glyphFor(category)),
                contentDescription = label,
                modifier = Modifier.size(WearListMetrics.LeadingIconNormal),
                tint = BrowseCategoryPresentation.tintFor(category.type)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun PhoneCategoryChip(
    category: WearBrowseCategory,
    onClick: () -> Unit
) {
    val label = stringResource(BrowseCategoryPresentation.labelFor(category))
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(BrowseCategoryPresentation.glyphFor(category)),
                contentDescription = null,
                modifier = Modifier.size(WearListMetrics.LeadingIconNormal),
                tint = BrowseCategoryPresentation.tintFor(category.type)
            )
        },
        // The row announces its own name, so the reading never degrades to a position in the list.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        colors = ChipDefaults.primaryChipColors()
    )
}

/**
 * A short row is padded with empty weights so its cells keep the width of a full row's cells - the
 * same rule `HomeScreen` applies, kept here because a `Spacer` addresses nothing and must not become
 * a cell that looks tappable.
 */
@Composable
private fun PhoneCategoryRow(
    categories: List<WearBrowseCategory>,
    columns: Int,
    onCategoryClick: (WearBrowseCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        categories.forEach { category ->
            PhoneCategoryCell(
                category = category,
                modifier = Modifier.weight(1f),
                onClick = { onCategoryClick(category) }
            )
        }
        repeat(columns - categories.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhoneCategoryCell(
    category: WearBrowseCategory,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val label = stringResource(BrowseCategoryPresentation.labelFor(category))
    ThumbnailCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = label,
        onClick = onClick,
        modifier = modifier
    ) { glyphModifier ->
        Icon(
            painter = painterResource(BrowseCategoryPresentation.glyphFor(category)),
            contentDescription = null,
            modifier = glyphModifier,
            tint = BrowseCategoryPresentation.tintFor(category.type)
        )
    }
}
