package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import javax.inject.Inject

/**
 * Effective scan filter for one resource: which media types are visible, and which file sizes pass.
 */
data class ScanFilter(
    val mediaTypes: Set<MediaType>,
    val sizeFilter: SizeFilter
)

/**
 * Single source of truth for "which files of this resource does the app see".
 *
 * S1584: the main-screen card counter and the Browse list each derived this independently, and the
 * counter's derivation was a superset on both axes - it ignored [MediaResource.supportedMediaTypes],
 * the flavor gate, and the user's per-type size ceilings. The card could therefore promise files the
 * list would never show (observed: card "45 files", list empty), and nothing signalled the gap. The
 * list is authoritative because it is what the user actually receives, so this use case reproduces
 * the list's derivation and both call sites resolve through it.
 */
class ResolveScanFilterUseCase @Inject constructor(
    private val mediaCapabilities: MediaCapabilities
) {

    operator fun invoke(resource: MediaResource, settings: AppSettings): ScanFilter {
        val requestedTypes = if (resource.allFiles) {
            MediaType.entries.toSet()
        } else {
            resource.supportedMediaTypes.intersect(settings.getGloballyEnabledMediaTypes())
        }
        return ScanFilter(
            mediaTypes = applyFlavorMediaTypeRestrictions(requestedTypes),
            sizeFilter = SizeFilter(
                imageSizeMin = settings.imageSizeMin,
                imageSizeMax = settings.imageSizeMax,
                videoSizeMin = settings.videoSizeMin,
                videoSizeMax = settings.videoSizeMax,
                audioSizeMin = settings.audioSizeMin,
                audioSizeMax = settings.audioSizeMax
            )
        )
    }

    /**
     * Same type set, size ceilings lifted. Distinguishes "the folder is empty" from "the size filter
     * removed everything", which is what lets the empty state explain itself instead of asserting it
     * checked twice.
     */
    fun withoutSizeCeiling(filter: ScanFilter): ScanFilter = filter.copy(
        sizeFilter = filter.sizeFilter.copy(
            imageSizeMax = Long.MAX_VALUE,
            videoSizeMax = Long.MAX_VALUE,
            audioSizeMax = Long.MAX_VALUE
        )
    )

    /**
     * Drop media types the current product flavor cannot handle (photos has no video/audio, lite has
     * no documents), so a resource carrying them never asks a scanner for files it cannot open.
     */
    private fun applyFlavorMediaTypeRestrictions(supportedTypes: Set<MediaType>): Set<MediaType> {
        return supportedTypes.filter { mediaType ->
            when (mediaType) {
                MediaType.VIDEO -> mediaCapabilities.supportsVideo
                MediaType.AUDIO -> mediaCapabilities.supportsAudio
                MediaType.IMAGE -> mediaCapabilities.supportsImages
                MediaType.GIF -> mediaCapabilities.supportsImages
                MediaType.TEXT -> mediaCapabilities.supportsDocuments
                MediaType.PDF -> mediaCapabilities.supportsDocuments
                MediaType.EPUB -> mediaCapabilities.supportsEpub
                MediaType.OFFICE_DOCUMENT -> mediaCapabilities.supportsDocuments
                MediaType.BINARY_ARCHIVE,
                MediaType.BINARY_DISK,
                MediaType.BINARY_EXECUTABLE,
                MediaType.BINARY_OTHER -> true
            }
        }.toSet()
    }
}
