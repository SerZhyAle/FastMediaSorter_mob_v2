package com.sza.fastmediasorter.wear.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.model.WearBrowseCategory
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.BrowseCategoryPresentation
import com.sza.fastmediasorter.wear.ui.common.CenteredGridRow
import com.sza.fastmediasorter.wear.ui.common.SingleColumnTileCell
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val TITLE_VERTICAL_PADDING = 12.dp

/**
 * Unified home category screen for any media origin (Local, Phone, Network).
 *
 * Displays a title, category items (in single-column chips or multi-column cell rows based on viewMode),
 * and an optional secondary Browse folder chip at the bottom when folder walking is available.
 */
@Composable
fun OriginHomeScreen(
    title: String,
    categories: List<WearBrowseCategory>,
    viewMode: WearViewMode,
    onCategoryClick: (WearBrowseCategory) -> Unit,
    onBack: () -> Unit,
    onFolderClick: (() -> Unit)? = null
) {
    val listState = rememberWearListState(initialItemIndex = 1)
    Timber.d("S2466: OriginHomeScreen composing with prescroll, title=%s", title)

    val vocabulary = categories.filterNot { it.token == BrowseCategoryCatalog.TOKEN_BROWSE }
    val folderCategory = categories.firstOrNull { it.token == BrowseCategoryCatalog.TOKEN_BROWSE }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        if (vocabulary.isEmpty() && folderCategory == null) {
            WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = stringResource(R.string.wear_media_types_all_disabled),
                onBack = onBack
            )
            return@WearScreenScaffold
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // S2495: the gap and the minimum tap target are passed here rather than defaulted, because
            // this call site is the one that decides how many cells share a chord of round glass. A
            // row that fits by the default and not by this screen's own padding puts its outer cell
            // past the edge, where it is drawn and cannot be tapped.
            val columns = GridColumnFit.columnsFor(
                mode = viewMode,
                availableWidthDp = maxWidth.value.toInt(),
                gapDp = GridColumnFit.DEFAULT_GAP_DP,
                minTargetDp = GridColumnFit.DEFAULT_MIN_TARGET_DP
            )
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                val folderChip = folderCategory.takeIf { onFolderClick != null }
                if (columns == SINGLE_COLUMN) {
                    items(vocabulary) { category ->
                        OriginCategoryChip(
                            category = category,
                            onClick = { onCategoryClick(category) }
                        )
                    }
                    // A full-width chip is the whole row here, so there is no row for it to share.
                    if (folderChip != null && onFolderClick != null) {
                        item {
                            OriginFolderChip(category = folderChip, onClick = onFolderClick)
                        }
                    }
                } else {
                    // S2495: browse takes part in the row layout instead of being forced onto a line of
                    // its own, which is what left the space beside it unusable. It stays last, so it
                    // fills a row only after every category has one.
                    val cells = if (folderChip != null) vocabulary + folderChip else vocabulary
                    Timber.d("S2495: %d cell(s) over %d column(s), browse in row", cells.size, columns)
                    items(cells.chunked(columns)) { rowCategories ->
                        OriginCategoryRow(
                            categories = rowCategories,
                            columns = columns,
                            onCategoryClick = { category ->
                                if (category.token == BrowseCategoryCatalog.TOKEN_BROWSE) {
                                    onFolderClick?.invoke()
                                } else {
                                    onCategoryClick(category)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginCategoryChip(
    category: WearBrowseCategory,
    onClick: () -> Unit
) {
    val label = stringResource(BrowseCategoryPresentation.labelFor(category))
    SingleColumnTileCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = label,
        onClick = onClick,
        colors = ChipDefaults.primaryChipColors(),
        fallback = { glyphModifier ->
            Icon(
                painter = painterResource(BrowseCategoryPresentation.glyphFor(category)),
                contentDescription = null,
                modifier = glyphModifier,
                tint = BrowseCategoryPresentation.tintFor(category.type)
            )
        }
    )
}

@Composable
private fun OriginFolderChip(
    category: WearBrowseCategory,
    onClick: () -> Unit
) {
    val label = stringResource(BrowseCategoryPresentation.labelFor(category))
    SingleColumnTileCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = label,
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        fallback = { glyphModifier ->
            Icon(
                painter = painterResource(BrowseCategoryPresentation.glyphFor(category)),
                contentDescription = null,
                modifier = glyphModifier,
                tint = BrowseCategoryPresentation.tintFor(category.type)
            )
        }
    )
}

@Composable
private fun OriginCategoryRow(
    categories: List<WearBrowseCategory>,
    columns: Int,
    onCategoryClick: (WearBrowseCategory) -> Unit
) {
    CenteredGridRow(columns = columns, itemCount = categories.size, gap = GRID_GAP) {
        categories.forEach { category ->
            OriginCategoryCell(
                category = category,
                modifier = Modifier.weight(1f),
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
private fun OriginCategoryCell(
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
