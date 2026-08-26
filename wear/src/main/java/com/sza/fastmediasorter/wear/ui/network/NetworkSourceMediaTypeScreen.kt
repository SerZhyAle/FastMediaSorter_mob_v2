package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.settings.SettingsViewModel
import com.sza.fastmediasorter.wear.util.GridColumnFit

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 12.dp

private data class SourceMediaCategory(
    val labelRes: Int,
    val mediaType: String,
    val icon: ImageVector
)

/**
 * S1829: the media type a network source is opened under.
 *
 * Until this screen existed, [NetworkSourcesScreen] was the only way into a network source and it
 * passed a hard-coded "music", so images and video on SMB/FTP/SFTP were unreachable from the watch
 * even though the code that lists and plays them was already written. The type could not come from
 * anywhere else: unlike the watch's own media and the phone's, a network origin holds many containers,
 * so its type belongs to the source the user picked rather than to the origin.
 *
 * The same three settings that hide a category on [com.sza.fastmediasorter.wear.ui.home.LocalHomeScreen]
 * hide it here, so a watch with video turned off never offers a route into a list it would refuse to
 * fill.
 */
@Composable
fun NetworkSourceMediaTypeScreen(
    navController: NavController,
    sourceId: String,
    sourceName: String,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val categories = listOf(
        SourceMediaCategory(R.string.music, MEDIA_TYPE_MUSIC, Icons.Filled.MusicNote),
        SourceMediaCategory(R.string.videos, MEDIA_TYPE_VIDEOS, Icons.Filled.VideoLibrary),
        SourceMediaCategory(R.string.photos, MEDIA_TYPE_PHOTOS, Icons.Filled.Image)
    ).filter { category ->
        when (category.mediaType) {
            MEDIA_TYPE_MUSIC -> settings.isAudioEnabled
            MEDIA_TYPE_VIDEOS -> settings.isVideoEnabled
            MEDIA_TYPE_PHOTOS -> settings.isImagesEnabled
            else -> true
        }
    }

    // A choice between one option is not a choice. Pass straight through and drop this screen from the
    // back stack, so Back returns to the source list rather than to a step that decided nothing.
    LaunchedEffect(categories, sourceId) {
        val only = categories.singleOrNull() ?: return@LaunchedEffect
        navController.navigate(WearRoutes.browseSource(only.mediaType, sourceId, sourceName)) {
            popUpTo(WearRoutes.SOURCE_MEDIA_TYPE_PATTERN) { inclusive = true }
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

        // The column count comes from the width this composable actually gets, never from the mode
        // name - the same rule the other browse screens apply, so this step cannot drift from them.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(settings.viewMode, maxWidth.value.toInt())
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
                scalingParams = WearGridScalingParams
            ) {
                item {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                categoryItems(
                    categories = categories,
                    columns = columns,
                    onCategoryClick = { category ->
                        navController.navigate(
                            WearRoutes.browseSource(category.mediaType, sourceId, sourceName)
                        )
                    }
                )
            }
        }
    }
}

private fun ScalingLazyListScope.categoryItems(
    categories: List<SourceMediaCategory>,
    columns: Int,
    onCategoryClick: (SourceMediaCategory) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(categories) { category ->
            CategoryChip(category = category, onClick = { onCategoryClick(category) })
        }
    } else {
        items(categories.chunked(columns)) { rowCategories ->
            CategoryRow(
                categories = rowCategories,
                columns = columns,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: SourceMediaCategory,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(text = stringResource(category.labelRes)) },
        icon = {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(CELL_ICON_SIZE)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.primaryChipColors()
    )
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun CategoryRow(
    categories: List<SourceMediaCategory>,
    columns: Int,
    onCategoryClick: (SourceMediaCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        categories.forEach { category ->
            CategoryCell(
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
private fun CategoryCell(
    category: SourceMediaCategory,
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
            imageVector = category.icon,
            contentDescription = null,
            modifier = glyphModifier
        )
    }
}

// The route argument values BrowseScreen parses back into MediaType; they are a navigation contract,
// not display text, so they stay literal on both ends.
private const val MEDIA_TYPE_MUSIC = "music"
private const val MEDIA_TYPE_VIDEOS = "videos"
private const val MEDIA_TYPE_PHOTOS = "photos"
