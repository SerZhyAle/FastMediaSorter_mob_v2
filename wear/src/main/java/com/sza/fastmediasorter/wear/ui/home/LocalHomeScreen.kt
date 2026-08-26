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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CHIP_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 12.dp

private data class LocalCategory(
    val labelRes: Int,
    val mediaType: String,
    val type: WearContentType
)

/**
 * Media stored on the watch itself, split by type.
 *
 * These three rows used to sit on the home screen; the home screen now lists origins, so they moved
 * one level down without changing what they do or which settings hide them.
 */
@Composable
fun LocalHomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Timber.d("S2003: local categories - columns from the saved view mode, catalog glyphs")

    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val categories = listOf(
        LocalCategory(R.string.music, "music", WearContentType.MUSIC),
        LocalCategory(R.string.videos, "videos", WearContentType.VIDEO),
        LocalCategory(R.string.photos, "photos", WearContentType.IMAGE)
    ).filter { category ->
        when (category.mediaType) {
            "music" -> settings.isAudioEnabled
            "videos" -> settings.isVideoEnabled
            "photos" -> settings.isImagesEnabled
            else -> true
        }
    }

    // A choice between one option is not a choice. Pass straight through and drop this screen from the
    // back stack, so Back returns to the home screen rather than to a step that decided nothing.
    LaunchedEffect(categories) {
        val only = categories.singleOrNull() ?: return@LaunchedEffect
        navController.navigate(WearRoutes.browse(only.mediaType)) {
            popUpTo(WearRoutes.LOCAL_HOME) { inclusive = true }
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        if (categories.isEmpty()) {
            // No retry: the list is empty because a settings read succeeded and returned three
            // disabled types, so repeating that read would return the same answer.
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
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets()
            ) {
                item {
                    Text(
                        text = stringResource(R.string.wear_section_local),
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                if (columns == SINGLE_COLUMN) {
                    items(categories) { category ->
                        LocalCategoryChip(
                            category = category,
                            onClick = { navController.navigate(WearRoutes.browse(category.mediaType)) }
                        )
                    }
                } else {
                    items(categories.chunked(columns)) { rowCategories ->
                        LocalCategoryRow(
                            categories = rowCategories,
                            columns = columns,
                            onCategoryClick = { category ->
                                navController.navigate(WearRoutes.browse(category.mediaType))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalCategoryChip(
    category: LocalCategory,
    onClick: () -> Unit
) {
    val label = stringResource(category.labelRes)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(ContentTypeCatalog.iconFor(category.type)),
                contentDescription = null,
                modifier = Modifier.size(CHIP_ICON_SIZE),
                tint = categoryTint(category.type)
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
private fun LocalCategoryRow(
    categories: List<LocalCategory>,
    columns: Int,
    onCategoryClick: (LocalCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        categories.forEach { category ->
            LocalCategoryCell(
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
private fun LocalCategoryCell(
    category: LocalCategory,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val label = stringResource(category.labelRes)
    ThumbnailCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = label,
        onClick = onClick,
        modifier = modifier
    ) { glyphModifier ->
        Icon(
            painter = painterResource(ContentTypeCatalog.iconFor(category.type)),
            contentDescription = null,
            modifier = glyphModifier,
            tint = categoryTint(category.type)
        )
    }
}

/**
 * The semantic tone for a category glyph, or none when the painter already carries its own colour.
 *
 * Same guard as `HomeScreen`: an already coloured vector must keep what it has, so the catalog is
 * asked rather than tinted blindly.
 */
@Composable
private fun categoryTint(type: WearContentType): Color =
    if (ContentTypeCatalog.isMonochrome(type)) {
        colorResource(ContentTypeCatalog.tintFor(type))
    } else {
        Color.Unspecified
    }
