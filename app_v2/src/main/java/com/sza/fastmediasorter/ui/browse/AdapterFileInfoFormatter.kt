package com.sza.fastmediasorter.ui.browse

import android.content.Context
import com.sza.fastmediasorter.core.util.formatFileSize
import com.sza.fastmediasorter.core.util.formatMediaDuration
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Pure formatting helpers for MediaFileAdapter list items. Still stateless - the object holds no fields.
 * The size-bearing builders take a [Context] because the unit label is a localized resource (S2351), not
 * because the formatter caches anything from it.
 */
object AdapterFileInfoFormatter {

    // Per-bind formatting hot path: cache one SimpleDateFormat per thread instead of re-parsing the
    // pattern and allocating Calendar/SpannableStringBuilder on every RecyclerView row. SimpleDateFormat
    // is not thread-safe, so confine via ThreadLocal. API23-safe form (no ThreadLocal.withInitial).
    private val timestampFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat("yy-MM-dd HH:mm", java.util.Locale.US)
    }

    /**
     * "yy-MM-dd HH:mm" timestamp. Locale.US is deliberate: the pattern is purely numeric (no localized
     * month/day names or AM/PM), and android.text.format.DateFormat.format - which this replaced - emits
     * ASCII digits for such patterns regardless of system locale, so the rendered text is unchanged for
     * every supported locale. A fixed locale also keeps the cached formatter deterministic.
     */
    fun formatTimestamp(millis: Long): String = timestampFormat.get()!!.format(java.util.Date(millis))

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
        val date = if (file.createdDate > 0) formatTimestamp(file.createdDate) else null
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
                val dateTaken = file.exifDateTime?.let { formatTimestamp(it) }
                val parts = listOfNotNull(resolution, dateTaken, sizeSegment)
                if (parts.isNotEmpty()) parts.joinToString(" • ") else legacyInfo
            }

            else -> legacyInfo
        }
    }

    private fun buildLegacyFileInfo(context: Context, file: MediaFile): String {
        // Hide invalid FTP metadata (size=0 or date=1970-01-01)
        val size = if (file.size > 0) formatFileSize(context, file.size) else "-"
        val date = if (file.createdDate > 0) formatTimestamp(file.createdDate) else "-"
        return "$size • $date"
    }

    private fun formatDuration(durationMs: Long?): String? = formatMediaDuration(durationMs)
}
