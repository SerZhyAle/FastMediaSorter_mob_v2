package com.sza.fastmediasorter.util

import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * CAPTURE-OUTPUT rule 16: a photo or image capture carries its capture time in EXIF
 * [ExifInterface.TAG_DATETIME_ORIGINAL], equal to the local wall-clock time in its file name.
 */
object ExifTimeWriter {

    const val EXIF_DATETIME_PATTERN = "yyyy:MM:dd HH:mm:ss"
    private const val EXIF_OFFSET_PATTERN = "XXX"

    /** Formats [timestampMillis] as EXIF date-time string in [timeZone]. */
    fun formatExifDateTime(
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return SimpleDateFormat(EXIF_DATETIME_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(timestampMillis))
    }

    /** Formats [timestampMillis] timezone offset as EXIF offset string (e.g. `+02:00`). */
    fun formatExifOffset(
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return SimpleDateFormat(EXIF_OFFSET_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(timestampMillis))
    }

    /**
     * Writes capture timestamp tags to [file] using [ExifInterface].
     * Sets [ExifInterface.TAG_DATETIME_ORIGINAL], [ExifInterface.TAG_DATETIME],
     * [ExifInterface.TAG_DATETIME_DIGITIZED], and [ExifInterface.TAG_OFFSET_TIME_ORIGINAL].
     */
    fun writeCaptureTime(
        file: File,
        timestampMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val dateStr = formatExifDateTime(timestampMillis, timeZone)
            val offsetStr = formatExifOffset(timestampMillis, timeZone)

            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateStr)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateStr)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offsetStr)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offsetStr)
            exif.saveAttributes()
            true
        }.getOrElse { error ->
            Timber.w(error, "ExifTimeWriter: failed to write EXIF capture time to ${file.name}")
            false
        }
    }

    /**
     * Reads the capture time from [file]'s EXIF tags ([ExifInterface.TAG_DATETIME_ORIGINAL]
     * preferred, falling back to [ExifInterface.TAG_DATETIME]).
     * Returns epoch milliseconds, or null if tags are absent or unparseable.
     */
    fun readCaptureTimeMillis(
        file: File,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr.isNullOrBlank()) return@runCatching null

            val offsetStr = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)

            val resolvedZone = if (!offsetStr.isNullOrBlank()) {
                TimeZone.getTimeZone("GMT$offsetStr")
            } else {
                timeZone
            }

            SimpleDateFormat(EXIF_DATETIME_PATTERN, Locale.US).apply {
                this.timeZone = resolvedZone
            }.parse(dateStr)?.time
        }.getOrNull()
    }
}
