package com.sza.fastmediasorter.ui.browse

import android.content.Context
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.core.util.formatMediaDuration
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.Quantity
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

/**
 * Formatting helpers for MediaFileAdapter list items. The size- and date-bearing builders take a
 * [Context] because the unit label is a localized resource (S2351) and because the measurement system
 * that decides the date order is resolved through it (S2795).
 */
object AdapterFileInfoFormatter {

    // S2795: this object is not in the injection graph, so it resolves the seam the way the project's
    // other out-of-graph surfaces do. The application-scoped singletons behind it are safe to hold; the
    // system itself is read per call, which is what makes a switched setting show on the next bind.
    @Volatile
    private var cachedSeam: UnitSystemEntryPoint? = null

    private fun seam(context: Context): UnitSystemEntryPoint = cachedSeam ?: EntryPointAccessors
        .fromApplication(context.applicationContext, UnitSystemEntryPoint::class.java)
        .also { cachedSeam = it }

    /**
     * Date and time in the user's measurement system - year-first and 24-hour under metric, the
     * American order and a 12-hour clock under imperial (S2795). The pattern is no longer chosen here:
     * a row that picks its own would drift from the same value shown in a dialog beside it.
     */
    fun formatTimestamp(context: Context, millis: Long): String {
        val entryPoint = seam(context)
        return entryPoint.quantityFormatter()
            .format(Quantity.DateTime(millis), entryPoint.unitSystemProvider().value)
    }

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
    fun buildAudioDetailLine(context: Context, file: MediaFile): String {
        val size = if (file.size > 0) formatFileSize(context, file.size) else null
        val date = if (file.createdDate > 0) formatTimestamp(context, file.createdDate) else null
        val duration = formatDuration(file.duration)
        return listOfNotNull(size, date, duration).joinToString(" • ")
    }

    /** Rich info line: resolution/duration for media, item count for folders, size+date otherwise. */
    fun buildFileInfo(context: Context, file: MediaFile): String {
        if (file.isDirectory) {
            val count = file.childCount ?: 0
            return when {
                count == 0 -> "Empty folder"
                count == 1 -> "1 item"
                else -> "$count items"
            }
        }

        val legacyInfo = buildLegacyFileInfo(context, file)
        // S0210: trailing size segment for rich rows; hidden when size unknown (FTP, partial metadata).
        val sizeSegment = if (file.size > 0) formatFileSize(context, file.size) else null

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
                val dateTaken = file.exifDateTime?.let { formatTimestamp(context, it) }
                val parts = listOfNotNull(resolution, dateTaken, sizeSegment)
                if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
            }

            else -> legacyInfo
        }
    }

    private fun buildLegacyFileInfo(context: Context, file: MediaFile): String {
        // Hide invalid FTP metadata (size=0 or date=1970-01-01)
        val size = if (file.size > 0) formatFileSize(context, file.size) else "-"
        val date = if (file.createdDate > 0) formatTimestamp(context, file.createdDate) else "-"
        return "$size • $date"
    }

    private fun formatDuration(durationMs: Long?): String? = formatMediaDuration(durationMs)
}
