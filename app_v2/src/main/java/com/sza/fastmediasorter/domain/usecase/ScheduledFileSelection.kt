package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.TimeFilter

/**
 * Which files a scheduled run picks up: the type mask that narrows the scan and the type, time and
 * condition filters applied to what the scan returned. Pure functions of the operation and the listing,
 * kept apart from [ExecuteScheduledOperationUseCase] so that class stays under the size limit (S4478).
 */
internal object ScheduledFileSelection {

    /**
     * Returns the source resource with supportedMediaTypes adjusted for the file type mask.
     * When ALL_FILES flag is set, passes all MediaType values to the scanner which activates
     * the isAllFilesMode (null-extensions) path in SmbMediaScanner, CloudMediaScanner, etc.
     * Subdirectory recursion is governed by resource.scanSubdirectories as configured by the user.
     */
    fun buildEffectiveResource(resource: MediaResource, mask: Int): MediaResource {
        return if (FileTypeFlags.isAllFiles(mask)) {
            resource.copy(
                supportedMediaTypes = MediaType.entries.toSet(),
                allFiles = true
            )
        } else {
            val types = mutableSetOf<MediaType>()
            if (FileTypeFlags.hasImages(mask)) types += setOf(MediaType.IMAGE, MediaType.GIF)
            if (FileTypeFlags.hasAudio(mask)) types += MediaType.AUDIO
            if (FileTypeFlags.hasVideo(mask)) types += MediaType.VIDEO
            if (FileTypeFlags.hasDocuments(mask)) {
                types += setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT, MediaType.OFFICE_DOCUMENT)
            }
            resource.copy(supportedMediaTypes = types)
        }
    }

    fun applyFilters(files: List<MediaFile>, op: ScheduledOperation): List<MediaFile> {
        val now = System.currentTimeMillis()
        return files
            // When ALL_FILES - type filtering was already applied at the scanner level via buildEffectiveResource
            .filter { if (FileTypeFlags.isAllFiles(op.fileTypeMask)) true else matchesTypeMask(it, op.fileTypeMask) }
            .filter { matchesTimeFilter(it, op.timeFilter, op.sinceLastMarkerAt, now) }
            .filter { op.fileConditions.matches(it, now) }
    }

    private fun matchesTypeMask(file: MediaFile, mask: Int): Boolean =
        (FileTypeFlags.hasImages(mask) && (file.type == MediaType.IMAGE || file.type == MediaType.GIF)) ||
            (FileTypeFlags.hasAudio(mask) && file.type == MediaType.AUDIO) ||
            (FileTypeFlags.hasVideo(mask) && file.type == MediaType.VIDEO) ||
            (FileTypeFlags.hasDocuments(mask) && file.type.isDocumentFile())

    private fun matchesTimeFilter(
        file: MediaFile,
        filter: TimeFilter,
        sinceMarker: Long?,
        now: Long
    ): Boolean = when (filter) {
        TimeFilter.ALL -> true
        TimeFilter.SINCE_LAST -> sinceMarker == null || file.createdDate > sinceMarker
        TimeFilter.LAST_HOUR -> file.createdDate > now - HOUR_MS
        TimeFilter.LAST_DAY -> file.createdDate > now - DAY_MS
    }

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L
}
