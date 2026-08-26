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
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberCloseAppAction
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val CELL_ICON_SIZE = 24.dp
private val TITLE_VERTICAL_PADDING = 16.dp

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen composing")

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    // Resolved here rather than inside the item slot: a slot is not the screen's remember scope, so
    // the action would be rebuilt every time the bar scrolls back into composition.
    val closeApp = rememberCloseAppAction()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        // The column count comes from the width this composable actually gets, never from the mode
        // name - a narrow round watch cannot give three columns a 48 dp target (strategic ADR-2).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = wearScreenInsets(),
                scalingParams = WearGridScalingParams
            ) {
                item {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.title2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = TITLE_VERTICAL_PADDING),
                        textAlign = TextAlign.Center
                    )
                }

                lastUsedItems(
                    shortcuts = uiState.lastUsedResources,
                    columns = columns,
                    onSectionClick = { section -> navController.navigate(section.route) }
                )

                sectionItems(
                    sections = uiState.sections,
                    columns = columns,
                    onSectionClick = { section -> navController.navigate(section.route) }
                )

                item {
                    HomeCommandBar(
                        onSettingsClick = { navController.navigate(WearRoutes.SETTINGS) },
                        onCloseClick = closeApp
                    )
                }
            }
        }
    }
}

private fun ScalingLazyListScope.sectionItems(
    sections: List<HomeSection>,
    columns: Int,
    onSectionClick: (HomeSection) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(sections) { section ->
            HomeSectionChip(section = section, onClick = { onSectionClick(section) })
        }
    } else {
        items(sections.chunked(columns)) { rowSections ->
            HomeSectionRow(
                sections = rowSections,
                columns = columns,
                onSectionClick = onSectionClick
            )
        }
    }
}

/**
 * S1974: the shortcuts own the whole first row, so every predefined section below keeps its cell no
 * matter how many resources have been opened.
 *
 * In a grid the row is emitted even when there is nothing to put in it - that empty row is precisely
 * what holds the sections still. A single column has no cells to misalign, so there it stays the
 * plain chip it was and a missing shortcut costs no vertical space at all (strategic ADR-1).
 */
private fun ScalingLazyListScope.lastUsedItems(
    shortcuts: List<HomeSection>,
    columns: Int,
    onSectionClick: (HomeSection) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(shortcuts) { section ->
            HomeSectionChip(section = section, onClick = { onSectionClick(section) })
        }
    } else {
        item {
            HomeSectionRow(
                // Never more cells than the row was measured for: GridColumnFit can refuse the mode's
                // requested column count on a narrow round display (strategic ADR-2).
                sections = shortcuts.take(columns),
                columns = columns,
                onSectionClick = onSectionClick
            )
        }
    }
}

@Composable
private fun HomeSectionChip(
    section: HomeSection,
    onClick: () -> Unit
) {
    val label = section.dynamicLabel ?: stringResource(section.labelRes)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(iconFor(section.id)),
                contentDescription = label,
                modifier = Modifier.size(CELL_ICON_SIZE)
            )
        },
        // A section announces its own name - the dynamic resource name where it has one - so the
        // reading never degrades to a position in the list.
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        colors = ChipDefaults.primaryChipColors()
    )
}

/**
 * A short row is padded with empty weights so its cells keep the width of a full row's cells.
 *
 * S1974: both the shortcut row and the section rows come through here, so the padding rule exists
 * once. A second copy is how the two rows would drift into different cell widths - the very defect
 * this ticket removes. An empty weight is a Spacer and nothing more: it takes no focus, announces
 * nothing and handles no click, because a cell that addresses nothing cannot navigate (ADR-4).
 */
@Composable
private fun HomeSectionRow(
    sections: List<HomeSection>,
    columns: Int,
    onSectionClick: (HomeSection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        sections.forEach { section ->
            HomeSectionCell(
                section = section,
                modifier = Modifier.weight(1f),
                onClick = { onSectionClick(section) }
            )
        }
        repeat(columns - sections.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeSectionCell(
    section: HomeSection,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val label = section.dynamicLabel ?: stringResource(section.labelRes)
    ThumbnailCell(
        thumbnail = WearThumbnail.Unavailable,
        caption = label,
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(iconFor(section.id)),
            contentDescription = null,
            modifier = Modifier.size(CELL_ICON_SIZE)
        )
    }
}

/**
 * Icons stay here rather than on the section model so the domain layer carries no Compose types.
 *
 * These are the phone's own vectors, copied into this module: one entity wears one glyph across both
 * apps, and `docs/ICON_LEGEND.md` is the table that decides which (owner instruction 2026-08-18).
 */
@DrawableRes
private fun iconFor(id: HomeSectionId): Int = when (id) {
    HomeSectionId.LAST_USED_RESOURCE -> R.drawable.ic_history
    HomeSectionId.FAVOURITES -> R.drawable.ic_resource_favorites
    // ic_resource is the phone's canonical umbrella glyph for "a source registered in this app",
    // which is what this section lists. ic_wifi described the transport, not the entity (S1952).
    HomeSectionId.RESOURCES -> R.drawable.ic_resource
    HomeSectionId.PHONE -> R.drawable.ic_profile_personal_smartphone
    // Local means the watch's own storage, so it takes the phone's glyph for the watch - the phone's
    // "local storage" icon is a smartphone and would have been indistinguishable from PHONE above.
    HomeSectionId.LOCAL -> R.drawable.ic_watch
    HomeSectionId.STREAMS -> R.drawable.ic_cast
    HomeSectionId.APPS -> R.drawable.ic_apps
}
