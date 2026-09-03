package com.sza.fastmediasorter.wear.ui.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.asContentType
import com.sza.fastmediasorter.wear.domain.model.contentTypeForMime
import com.sza.fastmediasorter.wear.ui.common.CellCaption
import com.sza.fastmediasorter.wear.ui.common.ContentTypeCatalog
import com.sza.fastmediasorter.wear.ui.common.LongPressChip
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.common.WearRowDensity
import com.sza.fastmediasorter.wear.ui.common.rowDensityFor
import com.sza.fastmediasorter.wear.util.GridColumnFit
import com.sza.fastmediasorter.wear.util.fileSizeParts
import com.sza.fastmediasorter.wear.util.formatWearDuration

private const val SINGLE_COLUMN = 1
private const val GRID_CAPTION_LINES = 2
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp

private const val IMAGE_PREFIX = "image/"
private const val VIDEO_PREFIX = "video/"
private const val AUDIO_PREFIX = "audio/"

private val SELECTION_BORDER_WIDTH = 2.dp
private val SELECTION_BADGE_SIZE = 16.dp

/** What a file cell can ask of the screen that owns the list. */
internal data class MediaFileActions(
    val onFileClick: (WearMediaFile) -> Unit,
    val onFileLongClick: (WearMediaFile) -> Unit,
    val onThumbnailNeeded: (WearMediaFile) -> Unit
)

/**
 * One column keeps today's informative chip; more than one trades the detail line for a picture.
 *
 * The column count is decided by the screen from the width it actually got and passed in here, so
 * two lists of the same size cannot answer the geometry question differently.
 */
internal fun ScalingLazyListScope.mediaFileItems(
    files: List<WearMediaFile>,
    columns: Int,
    thumbnails: Map<Long, WearThumbnail>,
    mediaType: MediaType,
    selectedIds: Set<Long>,
    actions: MediaFileActions
) {
    // Decided here, for the whole list, rather than per row: a picture that lands mid-scroll must not
    // re-size the glyph under the reading finger (strategic ADR-3). Only the cell path ever swaps a
    // glyph for a thumbnail, so the column count is what answers that question.
    val density = rowDensityFor(
        types = files.map { contentTypeForMime(it.mimeType) ?: mediaType.asContentType() },
        canProduceThumbnails = columns != SINGLE_COLUMN
    )
    if (columns == SINGLE_COLUMN) {
        items(files, key = { it.id }) { file ->
            MediaFileChip(
                file = file,
                mediaType = mediaType,
                selected = file.id in selectedIds,
                density = density,
                onClick = { actions.onFileClick(file) },
                onLongClick = { actions.onFileLongClick(file) }
            )
        }
    } else {
        items(files.chunked(columns)) { rowFiles ->
            MediaFileRow(
                files = rowFiles,
                columns = columns,
                thumbnails = thumbnails,
                mediaType = mediaType,
                selectedIds = selectedIds,
                actions = actions
            )
        }
    }
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun MediaFileRow(
    files: List<WearMediaFile>,
    columns: Int,
    thumbnails: Map<Long, WearThumbnail>,
    mediaType: MediaType,
    selectedIds: Set<Long>,
    actions: MediaFileActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        files.forEach { file ->
            // Asking here rather than up front keeps the read tied to a cell that is on screen.
            LaunchedEffect(file.id) { actions.onThumbnailNeeded(file) }
            val selected = file.id in selectedIds
            Box(modifier = Modifier.weight(1f)) {
                ThumbnailCell(
                    thumbnail = thumbnails[file.id] ?: WearThumbnail.Loading,
                    caption = file.name,
                    onClick = { actions.onFileClick(file) },
                    modifier = selectionFrame(selected),
                    onLongClick = { actions.onFileLongClick(file) },
                    // A file with a thumbnail keeps the two-line caption under its own picture; one
                    // without shows the mime-type glyph shared by every file of that type, and there
                    // the name moves onto it (S2177).
                    captionLayout = CellCaption(
                        maxLines = GRID_CAPTION_LINES,
                        overGroupIcon = true
                    )
                ) { glyphModifier ->
                    // The inset is the cell's, not the glyph's: ThumbnailCell hands the S2003
                    // placeholder contract down already sized, so this slot applies it untouched
                    // instead of choosing a size of its own.
                    val type = contentTypeForMime(file.mimeType) ?: mediaType.asContentType()
                    Icon(
                        painter = painterResource(ContentTypeCatalog.iconFor(type)),
                        // The cell already announces the file by name; a second description would
                        // make a screen reader read the same cell twice.
                        contentDescription = null,
                        modifier = glyphModifier,
                        tint = typeTint(type)
                    )
                }
                if (selected) {
                    SelectionBadge(modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
        repeat(columns - files.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MediaFileChip(
    file: WearMediaFile,
    mediaType: MediaType,
    selected: Boolean,
    density: WearRowDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val secondaryText = when {
        file.mimeType?.startsWith(IMAGE_PREFIX) == true -> formatFileSize(file.size)
        file.mimeType?.startsWith(VIDEO_PREFIX) == true -> durationBadge(file.duration)
        file.mimeType?.startsWith(AUDIO_PREFIX) == true -> durationBadge(file.duration)
        else -> ""
    }

    // The library Chip owns its own clickable and never sees a press handed in through a modifier,
    // so the row that has to serve both gestures is the module's own (S1953).
    LongPressChip(
        onClick = onClick,
        onLongClick = onLongClick,
        label = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(selectionFrame(selected)),
        // Both glyphs take the same size, or selecting a row would resize its leading slot.
        icon = if (selected) {
            { SelectionBadge(size = density.leadingIconSize) }
        } else {
            { TypeBadge(file = file, mediaType = mediaType, size = density.leadingIconSize) }
        },
        // The glyph used to lead this line, so it was never empty. Now that the type has its own
        // slot, a file whose mime type classifies nothing has nothing to say here, and an empty
        // Text would still claim a second line and push the name off the row's centre.
        secondaryLabel = if (secondaryText.isEmpty()) {
            null
        } else {
            { Text(text = secondaryText) }
        },
        colors = ChipDefaults.secondaryChipColors()
    )
}

/**
 * A selected cell is marked by shape as well as by the badge: colour alone would leave the state
 * unreadable to the accessibility requirement in strategic §3.2.
 */
@Composable
private fun selectionFrame(selected: Boolean): Modifier = if (selected) {
    Modifier.border(SELECTION_BORDER_WIDTH, MaterialTheme.colors.primary, MaterialTheme.shapes.small)
} else {
    Modifier
}

@Composable
private fun SelectionBadge(modifier: Modifier = Modifier, size: Dp = SELECTION_BADGE_SIZE) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = stringResource(R.string.wear_file_selected),
        modifier = modifier.size(size),
        tint = MaterialTheme.colors.primary
    )
}

/**
 * The type glyph a row wears while nothing is selected, so its icon slot is never empty.
 *
 * The caller hands both this glyph and the selection badge one size, so opening a selection swaps
 * one glyph for the other without shifting the row's height.
 */
@Composable
private fun TypeBadge(file: WearMediaFile, mediaType: MediaType, size: Dp) {
    val type = contentTypeForMime(file.mimeType) ?: mediaType.asContentType()
    Icon(
        painter = painterResource(ContentTypeCatalog.iconFor(type)),
        contentDescription = stringResource(typeNameRes(type)),
        modifier = Modifier.size(size),
        tint = typeTint(type)
    )
}

/**
 * The semantic tone for a type glyph, or none when the painter already carries its own colour.
 *
 * Same guard as the home screens: an already coloured vector must keep what it has, so the catalog
 * is asked rather than tinted blindly.
 */
@Composable
private fun typeTint(type: WearContentType): Color = if (ContentTypeCatalog.isMonochrome(type)) {
    colorResource(ContentTypeCatalog.tintFor(type))
} else {
    Color.Unspecified
}

/** What a screen reader calls the glyph, so the type is announced as an entity of its own. */
@StringRes
private fun typeNameRes(type: WearContentType): Int = when (type) {
    WearContentType.MUSIC -> R.string.wear_content_type_music
    WearContentType.VIDEO -> R.string.wear_content_type_video
    WearContentType.IMAGE -> R.string.wear_content_type_image
    // The resolver feeding this badge yields only the three media kinds. The rest of the catalog's
    // enum belongs to other surfaces, so the badge names them generically rather than announcing a
    // kind it could not have derived.
    WearContentType.DOCUMENT,
    WearContentType.FOLDER,
    WearContentType.STREAM,
    WearContentType.OTHER -> R.string.wear_content_type_other
}

/**
 * Composable so the unit is read through the composition's configuration rather than a context's: the
 * label has to follow the watch's language, which the arithmetic behind it never sees (S2353).
 */
@Composable
private fun formatFileSize(bytes: Long): String {
    val parts = fileSizeParts(bytes)
    return stringResource(parts.unitRes, parts.value)
}

// S2278: the badge hides an unknown duration entirely, which the shared formatter deliberately
// does not do - a player at position zero must still show a figure.
private fun durationBadge(durationMs: Long): String =
    if (durationMs > 0L) formatWearDuration(durationMs) else ""
