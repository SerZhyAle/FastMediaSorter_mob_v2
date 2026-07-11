package com.sza.fastmediasorter.ui.xr.browse

import com.sza.fastmediasorter.core.xr.VrMediaType
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.ui.player.StereoDetector
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import timber.log.Timber

/**
 * Reads the immersive browser content through the same domain layer the flat Browse screen uses
 * ([GetMediaFilesUseCase]) instead of the diagnostic ad-hoc folder scan, so the in-headset browser
 * stays consistent with the resource model. 3D images/videos are tagged via the shared
 * [StereoDetector] so the grid can badge stereo content without a second parser.
 */
class ImmersiveBrowseContentLoader @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val stereoDetector: StereoDetector,
) {

    /**
     * Loads one directory level of a resource as browse cells. [currentPath] null = resource root;
     * non-null = a subfolder for in-headset drill-down. Returns empty on a missing resource.
     */
    suspend fun load(resourceId: Long, currentPath: String?): List<ImmersiveBrowseCell> {
        val resource = getResourcesUseCase.getById(resourceId)
        if (resource == null) {
            Timber.w("ImmersiveBrowseContentLoader: resource %d not found", resourceId)
            return emptyList()
        }
        val files = getMediaFilesUseCase(
            resource = resource,
            currentPath = currentPath,
            isSubfolderMode = currentPath != null,
        ).first()

        return files
            .filter { it.isDirectory || it.type in BROWSABLE_TYPES }
            .mapIndexed { index, file -> file.toCell(index) }
    }

    private fun MediaFile.toCell(index: Int): ImmersiveBrowseCell = ImmersiveBrowseCell(
        index = index,
        label = name,
        isFolder = isDirectory,
        mediaType = type.toVrMediaType(),
        folderPath = if (isDirectory) path else null,
        filePath = if (isDirectory) null else (contentUri ?: path),
        stereoBadge = if (isDirectory) null else stereoDetector.detectFromFilename(name).toBadge(),
    )

    private fun MediaType.toVrMediaType(): VrMediaType = when (this) {
        MediaType.VIDEO -> VrMediaType.VIDEO
        MediaType.GIF -> VrMediaType.GIF
        else -> VrMediaType.IMAGE
    }

    /** Short projection/layout tag for the grid pill; null when the file is plain 2D. */
    private fun StereoMode.toBadge(): String? = when (this) {
        StereoMode.EQUIRECT_360_MONO,
        StereoMode.EQUIRECT_360_SBS,
        StereoMode.EQUIRECT_360_OU -> BADGE_360
        StereoMode.EQUIRECT_180_MONO,
        StereoMode.EQUIRECT_180_SBS,
        StereoMode.VR180_FISHEYE_SBS,
        StereoMode.CYLINDER_180 -> BADGE_180
        StereoMode.SBS_FULL,
        StereoMode.SBS_HALF -> BADGE_SBS
        StereoMode.OU -> BADGE_OU
        StereoMode.MONO,
        StereoMode.AUTO,
        StereoMode.UNKNOWN -> null
    }

    private companion object {
        val BROWSABLE_TYPES = setOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.GIF)
        const val BADGE_360 = "360"
        const val BADGE_180 = "180"
        const val BADGE_SBS = "SBS"
        const val BADGE_OU = "OU"
    }
}
