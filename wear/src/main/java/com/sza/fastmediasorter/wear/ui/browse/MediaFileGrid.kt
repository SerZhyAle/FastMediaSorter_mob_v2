package com.sza.fastmediasorter.wear.ui.browse

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.ui.common.LongPressChip
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.util.GridColumnFit
import java.util.Locale

private const val SINGLE_COLUMN = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp

private const val IMAGE_PREFIX = "image/"
private const val VIDEO_PREFIX = "video/"
private const val AUDIO_PREFIX = "audio/"

private const val PHOTO_ICON = "🖼️"
private const val VIDEO_ICON = "🎬"
private const val AUDIO_ICON = "🎵"

private const val BYTES_PER_UNIT = 1024.0
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_SECOND = 1000

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
    if (columns == SINGLE_COLUMN) {
        items(files, key = { it.id }) { file ->
            MediaFileChip(
                file = file,
                mediaType = mediaType,
                selected = file.id in selectedIds,
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
                    onLongClick = { actions.onFileLongClick(file) }
                ) { _ ->
                    // The cell offers the placeholder modifier and this slot cannot use it: the
                    // marker here is an emoji in a Text, whose glyph scales with font size and not
                    // with a modifier. Retiring the emoji for the catalog glyph belongs to S2004
                    // (strategic ADR-5), which is where this slot starts honouring the contract.
                    Text(
                        text = typeIcon(file, mediaType),
                        style = MaterialTheme.typography.title3
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val secondaryText = when {
        file.mimeType?.startsWith(IMAGE_PREFIX) == true -> formatFileSize(file.size)
        file.mimeType?.startsWith(VIDEO_PREFIX) == true -> formatDuration(file.duration)
        file.mimeType?.startsWith(AUDIO_PREFIX) == true -> formatDuration(file.duration)
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
        icon = if (selected) {
            { SelectionBadge() }
        } else {
            null
        },
        secondaryLabel = {
            Text(text = "${typeIcon(file, mediaType)} $secondaryText")
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
private fun SelectionBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = stringResource(R.string.wear_file_selected),
        modifier = modifier.size(SELECTION_BADGE_SIZE),
        tint = MaterialTheme.colors.primary
    )
}

/** The file's own type decides the glyph; the screen's type answers only for an unknown one. */
private fun typeIcon(file: WearMediaFile, mediaType: MediaType): String = when {
    file.mimeType?.startsWith(IMAGE_PREFIX) == true -> PHOTO_ICON
    file.mimeType?.startsWith(VIDEO_PREFIX) == true -> VIDEO_ICON
    file.mimeType?.startsWith(AUDIO_PREFIX) == true -> AUDIO_ICON
    else -> when (mediaType) {
        MediaType.MUSIC -> AUDIO_ICON
        MediaType.VIDEO -> VIDEO_ICON
        MediaType.PHOTO -> PHOTO_ICON
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = durationMs / MILLIS_PER_SECOND
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (minutes >= MINUTES_PER_HOUR) {
        val hours = minutes / MINUTES_PER_HOUR
        val remainingMinutes = minutes % MINUTES_PER_HOUR
        String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    val kilobytes = bytes / BYTES_PER_UNIT
    val megabytes = kilobytes / BYTES_PER_UNIT
    return when {
        megabytes >= 1 -> String.format(Locale.US, "%.1f MB", megabytes)
        kilobytes >= 1 -> String.format(Locale.US, "%.1f KB", kilobytes)
        else -> "$bytes B"
    }
}
