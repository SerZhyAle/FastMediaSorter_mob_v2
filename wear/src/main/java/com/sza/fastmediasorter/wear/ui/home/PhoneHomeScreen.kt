package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.DrawableRes
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

/**
 * @property iconOverride set only where the row is not a content type and so has no catalog glyph -
 * recents is a time filter, which is why it keeps its own symbol and borrows only the tone.
 */
private data class PhoneCategory(
    val labelRes: Int,
    val mediaType: String,
    val type: WearContentType,
    @DrawableRes val iconOverride: Int? = null
)

/**
 * The phone's virtual resources, reached from the watch.
 *
 * Registered sources that live on the phone are deliberately absent: they belong to the Resources
 * section, which is the whole point of splitting these two by content origin rather than media type.
 * The last row keeps the paired-phone folder browser reachable - it is a separate capability that
 * only ever had its entrance on the home screen.
 */
@Composable
fun PhoneHomeScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    Timber.d("S2003: phone categories - columns from the saved view mode, catalog glyphs")

    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    val categories = listOf(
        PhoneCategory(
            R.string.wear_phone_recents,
            "recents",
            WearContentType.OTHER,
            iconOverride = R.drawable.ic_history
        ),
        PhoneCategory(R.string.wear_phone_video, "videos", WearContentType.VIDEO),
        PhoneCategory(R.string.wear_phone_audio, "music", WearContentType.MUSIC),
        PhoneCategory(R.string.wear_phone_images, "photos", WearContentType.IMAGE),
        PhoneCategory(R.string.wear_phone_documents, "documents", WearContentType.DOCUMENT),
        PhoneCategory(R.string.wear_phone_all_files, "all", WearContentType.OTHER)
    )

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        // The six categories are declared above and never filtered, so this branch does not run
        // today. It is here because the category vocabulary is S2051's to decide: the moment that
        // ticket makes a row conditional, this screen already reports emptiness like its two
        // siblings instead of drawing a header over nothing. The generic message is deliberate -
        // unlike the sibling screens, nothing here is switched off in settings, so naming settings
        // as the cause would be false.
        if (categories.isEmpty()) {
            WearStateBlock(
                kind = WearStateKind.EMPTY,
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
                                navController.navigate(WearRoutes.browsePhone(category.mediaType))
                            }
                        )
                    }
                } else {
                    items(categories.chunked(columns)) { rowCategories ->
                        PhoneCategoryRow(
                            categories = rowCategories,
                            columns = columns,
                            onCategoryClick = { category ->
                                navController.navigate(WearRoutes.browsePhone(category.mediaType))
                            }
                        )
                    }
                }

                item {
                    PhoneFolderChip(
                        onClick = { navController.navigate(WearRoutes.PHONE_RESOURCE) }
                    )
                }
            }
        }
    }
}

/**
 * A full-width row of its own in every mode: the paired-phone folder browser is a separate
 * capability, and a cell in the type grid would read as a seventh content category.
 */
@Composable
private fun PhoneFolderChip(onClick: () -> Unit) {
    val label = stringResource(R.string.phone_resource_title)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(ContentTypeCatalog.iconFor(WearContentType.FOLDER)),
                contentDescription = label,
                modifier = Modifier.size(CHIP_ICON_SIZE),
                tint = categoryTint(WearContentType.FOLDER)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun PhoneCategoryChip(
    category: PhoneCategory,
    onClick: () -> Unit
) {
    val label = stringResource(category.labelRes)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(glyphFor(category)),
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
private fun PhoneCategoryRow(
    categories: List<PhoneCategory>,
    columns: Int,
    onCategoryClick: (PhoneCategory) -> Unit
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
    category: PhoneCategory,
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
            painter = painterResource(glyphFor(category)),
            contentDescription = null,
            modifier = glyphModifier,
            tint = categoryTint(category.type)
        )
    }
}

/** The row's own glyph where it has one, otherwise the catalog's for its content type. */
@DrawableRes
private fun glyphFor(category: PhoneCategory): Int =
    category.iconOverride ?: ContentTypeCatalog.iconFor(category.type)

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
