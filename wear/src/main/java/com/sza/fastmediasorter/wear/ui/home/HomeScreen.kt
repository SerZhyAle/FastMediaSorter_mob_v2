package com.sza.fastmediasorter.wear.ui.home

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import com.sza.fastmediasorter.wear.domain.model.HomeSection
import com.sza.fastmediasorter.wear.domain.model.HomeSectionId
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.apps.WearAppAccentCatalog
import com.sza.fastmediasorter.wear.ui.apps.WearAppIconCatalog
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.SingleColumnTileCell
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WEAR_LIST_UNTITLED_ANCHOR
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordance
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceRole
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberCloseAppAction
import com.sza.fastmediasorter.wear.ui.common.rememberMinimizeAppAction
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearBackAffordanceInset
import com.sza.fastmediasorter.wear.ui.icon.WearResourceIconRegistry
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags
import com.sza.fastmediasorter.wear.util.GridColumnFit
import kotlinx.coroutines.launch
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
    val isBackgroundPlaybackActive by viewModel.isBackgroundPlaybackActive.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    // No title item here, so the second data row is item 1 (S2466).
    val listState =
        rememberWearListState(initialCenterItemIndex = WEAR_LIST_UNTITLED_ANCHOR, positionKey = WearRoutes.HOME)
    // Resolved here rather than inside the item slot: a slot is not the screen's remember scope, so
    // the action would be rebuilt every time the bar scrolls back into composition.
    val closeApp = rememberCloseAppAction()
    val minimizeApp = rememberMinimizeAppAction()
    val shortcutClickScope = rememberCoroutineScope()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        // The column count comes from the width this composable actually gets, never from the mode
        // name - a narrow round watch cannot give three columns a 48 dp target (strategic ADR-2).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = GridColumnFit.columnsFor(uiState.viewMode, maxWidth.value.toInt())
            WearListColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                // S2524: the first thing on the screen while sound is playing, because the problem
                // this solves is that there was nowhere to arrive at. It is its own item and never
                // enters the chunked grid below: a row that appears and disappears inside that grid
                // would move every section cell each time the sound started (strategic ADR-1).
                nowPlaying?.let { playing ->
                    item {
                        HomeNowPlayingRow(
                            nowPlaying = playing,
                            onOpen = { fileId -> navController.navigate(WearRoutes.audioPlayer(fileId)) },
                            onStop = viewModel::stopBackgroundPlayback
                        )
                    }
                }

                // S2499: a shortcut is resolved before it is navigated to - a channel has no address
                // until playback preparation has run. A target that stopped resolving between the
                // draw and the tap leaves the screen where it is, which the ViewModel logs.
                lastUsedItems(
                    shortcuts = uiState.lastUsedResources,
                    columns = columns,
                    getFaviconTile = viewModel::getFaviconTile,
                    onSectionClick = { section ->
                        shortcutClickScope.launch {
                            viewModel.resolveShortcutRoute(section)?.let(navController::navigate)
                        }
                    }
                )

                sectionItems(
                    sections = uiState.sections,
                    columns = columns,
                    getFaviconTile = viewModel::getFaviconTile,
                    // S2751: the predefined rows resolve through the same path the shortcut row
                    // above uses. A catalogued section carries no address of its own any more - it is
                    // addressed by its id, and one resolution path keeps the two entrances identical.
                    onSectionClick = { section ->
                        shortcutClickScope.launch {
                            viewModel.resolveShortcutRoute(section)?.let(navController::navigate)
                        }
                    }
                )

                item {
                    HomeCommandBar(
                        onSettingsClick = { navController.navigate(WearRoutes.SETTINGS) }
                    )
                }
            }
        }
        // S2472: the home affordance, in the same left-middle band every other screen draws it in.
        // The cross and the chevron are one control, not two: which face it wears follows the live
        // playback state, and each face carries its own action - close stops the sound, minimize
        // keeps it - so the sign can never promise what the tap does not do.
        //
        // Declared AFTER the list, and that order is the whole of whether it works: siblings of a Box
        // are hit-tested back to front, so the full-size scrolling column declared after this control
        // takes every pointer over it and the cross is drawn but dead - the state the owner reported
        // on 2026-09-04, where the node was not even in the uiautomator tree. The host's own arrow
        // sits after the NavHost for the same reason, which is why Back worked while Close did not.
        WearBackAffordance(
            role = if (isBackgroundPlaybackActive) {
                WearBackAffordanceRole.Minimize
            } else {
                WearBackAffordanceRole.Close
            },
            onClick = {
                if (isBackgroundPlaybackActive) minimizeApp() else closeApp()
            },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = wearBackAffordanceInset())
        )
    }
}

private fun ScalingLazyListScope.sectionItems(
    sections: List<HomeSection>,
    columns: Int,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onSectionClick: (HomeSection) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(sections) { section ->
            HomeSectionChip(
                section = section,
                getFaviconTile = getFaviconTile,
                onClick = { onSectionClick(section) }
            )
        }
    } else {
        items(sections.chunked(columns)) { rowSections ->
            HomeSectionRow(
                sections = rowSections,
                columns = columns,
                getFaviconTile = getFaviconTile,
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
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onSectionClick: (HomeSection) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(shortcuts) { section ->
            HomeSectionChip(
                section = section,
                getFaviconTile = getFaviconTile,
                onClick = { onSectionClick(section) }
            )
        }
    } else {
        item {
            HomeSectionRow(
                // Never more cells than the row was measured for: GridColumnFit can refuse the mode's
                // requested column count on a narrow round display (strategic ADR-2).
                sections = shortcuts.take(columns),
                columns = columns,
                getFaviconTile = getFaviconTile,
                onSectionClick = onSectionClick
            )
        }
    }
}

@Composable
private fun HomeSectionChip(
    section: HomeSection,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onClick: () -> Unit
) {
    val label = section.dynamicLabel ?: stringResource(section.labelRes)
    val faviconBitmap by produceState<Bitmap?>(initialValue = null, section.faviconIndex) {
        value = getFaviconTile(section.faviconIndex)
    }
    val thumbnail = faviconBitmap?.let { WearThumbnail.Ready(it) } ?: WearThumbnail.Unavailable
    val glyph = glyphFor(section)
    SingleColumnTileCell(
        thumbnail = thumbnail,
        caption = label,
        onClick = onClick,
        modifier = Modifier.testTag(WearTestTags.homeSection(section.id)),
        fallback = { glyphModifier ->
            Icon(
                painter = painterResource(glyph.painterRes),
                contentDescription = null,
                modifier = glyphModifier,
                tint = glyphTint(section, glyph)
            )
        }
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
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onSectionClick: (HomeSection) -> Unit
) {
    com.sza.fastmediasorter.wear.ui.common.CenteredGridRow(
        columns = columns,
        itemCount = sections.size,
        gap = GRID_GAP
    ) {
        sections.forEach { section ->
            HomeSectionCell(
                section = section,
                modifier = Modifier.weight(1f),
                getFaviconTile = getFaviconTile,
                onClick = { onSectionClick(section) }
            )
        }
    }
}

@Composable
private fun HomeSectionCell(
    section: HomeSection,
    modifier: Modifier,
    getFaviconTile: suspend (Int?) -> Bitmap?,
    onClick: () -> Unit
) {
    val label = section.dynamicLabel ?: stringResource(section.labelRes)
    val faviconBitmap by produceState<Bitmap?>(initialValue = null, section.faviconIndex) {
        value = getFaviconTile(section.faviconIndex)
    }
    val thumbnail = faviconBitmap?.let { WearThumbnail.Ready(it) } ?: WearThumbnail.Unavailable
    ThumbnailCell(
        thumbnail = thumbnail,
        caption = label,
        onClick = onClick,
        modifier = modifier.testTag(WearTestTags.homeSection(section.id))
    ) { glyphModifier ->
        val glyph = glyphFor(section)
        Icon(
            painter = painterResource(glyph.painterRes),
            contentDescription = null,
            modifier = glyphModifier,
            tint = glyphTint(section, glyph)
        )
    }
}

/**
 * The tone a section glyph is drawn in: none for a vector that carries its own colour, the program's
 * accent for the row that stands for a program, the section's semantic tone otherwise.
 *
 * S3116: one function rather than the same three-way choice repeated in the chip and the cell, which
 * is how the two would come to draw one program in two colours.
 */
@Composable
private fun glyphTint(section: HomeSection, glyph: SectionGlyph): Color = when {
    glyph.ownsItsColour -> Color.Unspecified
    // The Apps list's accent for the same program, so the row and the cell are recognisably one thing.
    section.appId != null -> colorResource(WearAppAccentCatalog.accentFor(section.appId))
    else -> sectionTint(contentTypeFor(section.id))
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
        // S3116: a program wears the same glyph on this row as in the Apps list - one entity, one glyph,
        // the rule S2509 already applied to the broadcast's two entrances. Its accent is applied below.
        ?: section.appId?.let { SectionGlyph(WearAppIconCatalog.iconFor(it), ownsItsColour = false) }
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
    // S2499: a recent channel is a channel, so it takes the same tone the Streams section does.
    HomeSectionId.STREAMS,
    HomeSectionId.LAST_USED_STREAM -> WearContentType.STREAM
    HomeSectionId.LAST_USED_RESOURCE,
    HomeSectionId.RESOURCES,
    HomeSectionId.PHONE,
    HomeSectionId.LOCAL,
    // S2509: OTHER rather than STREAM. This row is a program of this app, not a channel registered
    // in it - giving it the stream tone would say the watch has a channel to play.
    HomeSectionId.BROADCAST,
    // S2551: OTHER for the same reason as the row above. What this one opens IS a stream, but it is
    // one that exists only while the session does - giving it the stream tone would place it beside
    // the registered channels, which is exactly what the ticket's non-goal keeps it out of.
    HomeSectionId.PHONE_CAMERA,
    // S3116: never reached while the row carries its program - the accent above answers first - and
    // OTHER when it does not, for the same reason as the Programs row: it is a program of this app.
    HomeSectionId.LAST_USED_APP,
    HomeSectionId.APPS -> WearContentType.OTHER
}

@DrawableRes
private fun iconFor(id: HomeSectionId): Int = HomeSectionIconCatalog.iconFor(id)
