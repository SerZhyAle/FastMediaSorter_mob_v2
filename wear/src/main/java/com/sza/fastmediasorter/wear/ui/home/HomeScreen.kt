package com.sza.fastmediasorter.wear.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearListMetrics
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberCloseAppAction
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.icon.WearResourceIconRegistry
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    Timber.d("HomeScreen composing")

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
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
                scalingParams = WearGridScalingParams,
                // One setting written twice: the state index says where to open, this says which
                // index may reach the centre at all. AutoCenteringParams defaults to 1 and pads
                // nothing below it, so setting the state alone leaves the request unhonoured.
                //
                // Top space is owned by autoCentering, but not exclusively: wearScreenInsets()
                // is a uniform inset on a round display, so its top share stacks on top of the
                // centring padding. Whether that stack is visible is a round-display measurement
                // (S2003 §3.3), so no number is guessed here - the device pass settles it.
                autoCentering = AutoCenteringParams(itemIndex = 0)
            ) {
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
    val glyph = glyphFor(section)
    Chip(
        onClick = onClick,
        label = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(glyph.painterRes),
                contentDescription = label,
                modifier = Modifier.size(WearListMetrics.LeadingIconNormal),
                tint = if (glyph.ownsItsColour) Color.Unspecified else LocalContentColor.current
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
    ) { glyphModifier ->
        val glyph = glyphFor(section)
        Icon(
            painter = painterResource(glyph.painterRes),
            contentDescription = null,
            modifier = glyphModifier,
            tint = if (glyph.ownsItsColour) {
                Color.Unspecified
            } else {
                sectionTint(contentTypeFor(section.id))
            }
        )
    }
}

/**
 * What a section draws in its icon slot: a vector plus whether that vector owns its colour.
 *
 * S2129: the last-used entries used to share one history glyph, so two or three recent resources
 * were impossible to tell apart on a small screen. A resource's own vector is coloured already, and
 * tinting it would repaint the very thing that distinguishes one row from the next.
 */
private data class SectionGlyph(@DrawableRes val painterRes: Int, val ownsItsColour: Boolean)

/**
 * The resource's own icon where the section carries one, otherwise the fixed glyph its id names.
 *
 * Only the last-used entries ever carry an icon id, so every other section reaches the fixed switch
 * with no special case, and an id this build does not have falls back there too.
 */
private fun glyphFor(section: HomeSection): SectionGlyph =
    WearResourceIconRegistry.resolveDrawable(section.iconId)
        ?.let { SectionGlyph(it, ownsItsColour = true) }
        ?: SectionGlyph(iconFor(section.id), ownsItsColour = false)

/**
 * The semantic tone for a section glyph, or none when the painter already carries its own colour.
 *
 * The guard is [ContentTypeCatalog.isMonochrome] rather than an unconditional tint: an already
 * coloured vector must keep what it has, which is exactly the favourites star's case.
 */
@Composable
private fun sectionTint(type: WearContentType?): Color =
    if (type != null && ContentTypeCatalog.isMonochrome(type)) {
        colorResource(ContentTypeCatalog.tintFor(type))
    } else {
        Color.Unspecified
    }

/**
 * Which content type a home section stands for, or null when it stands for none.
 *
 * This screen lists origins, not content types, so only streams names one outright. The rest take
 * the catalog's `OTHER` tone, which is the umbrella the catalog already documents for "a source
 * registered in this app" - the same reading that gave the Resources section its glyph.
 *
 * Favourites is null deliberately: `ic_resource_favorites` is a fixed amber badge with no tint hook,
 * so a semantic tone would repaint the star (strategic §11 criterion 7).
 */
private fun contentTypeFor(id: HomeSectionId): WearContentType? = when (id) {
    HomeSectionId.FAVOURITES -> null
    HomeSectionId.STREAMS -> WearContentType.STREAM
    HomeSectionId.LAST_USED_RESOURCE,
    HomeSectionId.RESOURCES,
    HomeSectionId.PHONE,
    HomeSectionId.LOCAL,
    HomeSectionId.APPS -> WearContentType.OTHER
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
