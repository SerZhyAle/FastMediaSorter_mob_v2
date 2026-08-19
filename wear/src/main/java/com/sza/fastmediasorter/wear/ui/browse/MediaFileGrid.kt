package com.sza.fastmediasorter.wear.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
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

/** What a file cell can ask of the screen that owns the list. */
internal data class MediaFileActions(
    val onFileClick: (WearMediaFile) -> Unit,
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
    actions: MediaFileActions
) {
    if (columns == SINGLE_COLUMN) {
        items(files, key = { it.id }) { file ->
            MediaFileChip(
                file = file,
                mediaType = mediaType,
                onClick = { actions.onFileClick(file) }
            )
        }
    } else {
        items(files.chunked(columns)) { rowFiles ->
            MediaFileRow(
                files = rowFiles,
                columns = columns,
                thumbnails = thumbnails,
                mediaType = mediaType,
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
    actions: MediaFileActions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        files.forEach { file ->
            // Asking here rather than up front keeps the read tied to a cell that is on screen.
            LaunchedEffect(file.id) { actions.onThumbnailNeeded(file) }
            ThumbnailCell(
                thumbnail = thumbnails[file.id] ?: WearThumbnail.Loading,
                caption = file.name,
                onClick = { actions.onFileClick(file) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = typeIcon(file, mediaType),
                    style = MaterialTheme.typography.title3
                )
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
    onClick: () -> Unit
) {
    val secondaryText = when {
        file.mimeType?.startsWith(IMAGE_PREFIX) == true -> formatFileSize(file.size)
        file.mimeType?.startsWith(VIDEO_PREFIX) == true -> formatDuration(file.duration)
        file.mimeType?.startsWith(AUDIO_PREFIX) == true -> formatDuration(file.duration)
        else -> ""
    }

    Chip(
        onClick = onClick,
        label = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(text = "${typeIcon(file, mediaType)} $secondaryText")
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors()
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
