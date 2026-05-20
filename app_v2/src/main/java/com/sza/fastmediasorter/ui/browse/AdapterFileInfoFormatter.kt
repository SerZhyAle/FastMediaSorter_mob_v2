package com.sza.fastmediasorter.ui.browse

import android.text.format.DateFormat
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.core.util.formatMediaDuration
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber
import java.util.Date

/** Pure formatting helpers for MediaFileAdapter list items. No state, no Android context. */
object AdapterFileInfoFormatter {

    /** "Artist - Title" for the top line in audio-only mode. Falls back to filename if metadata absent. */
    fun buildAudioDisplayName(file: MediaFile): String {
        val result = when {
            !file.artist.isNullOrBlank() && !file.title.isNullOrBlank() -> "${file.artist} - ${file.title}"
            !file.artist.isNullOrBlank() -> file.artist
            !file.title.isNullOrBlank() -> file.title
            else -> file.name
        }
        // Guard against invisible characters from malformed ID3 tags (BOM, NUL, etc.)
        val trimmed = result.trim()
        if (trimmed.isEmpty() || trimmed.all { it.code < 32 || it == ' ' || it == '\uFEFF' }) {
            Timber.w(
                "buildAudioDisplayName: invisible result for '${file.name}' | " +
                    "artist.codes=${file.artist?.map { it.code }?.take(8)} | " +
                    "title.codes=${file.title?.map { it.code }?.take(8)}"
            )
            return file.name
        }
        return result
    }

    /** "size • date • duration" for the bottom line in audio-only mode. */
    fun buildAudioDetailLine(file: MediaFile): String {
        val size = if (file.size > 0) formatFileSize(file.size) else null
        val date = if (file.createdDate > 0) DateFormat.format("yy-MM-dd HH:mm", Date(file.createdDate)).toString() else null
        val duration = formatDuration(file.duration)
        return listOfNotNull(size, date, duration).joinToString(" • ")
    }

    /** Rich info line: resolution/duration for media, item count for folders, size+date otherwise. */
    fun buildFileInfo(file: MediaFile): String {
        if (file.isDirectory) {
            val count = file.childCount ?: 0
            return when {
                count == 0 -> "Empty folder"
                count == 1 -> "1 item"
                else -> "$count items"
            }
        }

        val legacyInfo = buildLegacyFileInfo(file)
        // S0210: trailing size segment for rich rows; hidden when size unknown (FTP, partial metadata).
        val sizeSegment = if (file.size > 0) formatFileSize(file.size) else null

        return when (file.type) {
            MediaType.AUDIO -> {
                val hasMetadata = !file.artist.isNullOrBlank() || !file.title.isNullOrBlank()
                val duration = formatDuration(file.duration)
                if (hasMetadata) {
                    val audioTitle = when {
                        !file.artist.isNullOrBlank() && !file.title.isNullOrBlank() -> "${file.artist} - ${file.title}"
                        !file.artist.isNullOrBlank() -> file.artist
                        else -> file.title ?: file.name
                    }
                    listOfNotNull(audioTitle, duration, sizeSegment).joinToString(" • ")
                } else {
                    // legacyInfo already contains size - do not append sizeSegment here to avoid duplication.
                    if (duration != null) "$legacyInfo • $duration" else legacyInfo
                }
            }

            MediaType.VIDEO -> {
                val resolution = if (file.width != null && file.height != null) "${file.width}x${file.height}" else null
                val duration = formatDuration(file.duration)
                val parts = listOfNotNull(resolution, duration, sizeSegment)
                if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
            }

            MediaType.IMAGE, MediaType.GIF -> {
                val resolution = if (file.width != null && file.height != null) "${file.width}x${file.height}" else null
                val dateTaken = file.exifDateTime?.let { DateFormat.format("yy-MM-dd HH:mm", Date(it)).toString() }
                val parts = listOfNotNull(resolution, dateTaken, sizeSegment)
                if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
            }

            else -> legacyInfo
        }
    }

    private fun buildLegacyFileInfo(file: MediaFile): String {
        // Hide invalid FTP metadata (size=0 or date=1970-01-01)
        val size = if (file.size > 0) formatFileSize(file.size) else "-"
        val date = if (file.createdDate > 0) DateFormat.format("yy-MM-dd HH:mm", Date(file.createdDate)).toString() else "-"
        return "$size • $date"
    }

    private fun formatDuration(durationMs: Long?): String? = formatMediaDuration(durationMs)
}
