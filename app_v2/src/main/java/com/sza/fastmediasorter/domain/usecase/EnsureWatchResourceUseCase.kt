package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.strategy.WearWatchResourceStrategy
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S2034: the paired watch resource, whether or not it existed a moment ago.
 *
 * Two surfaces now reach the same row - the add-resource wizard and the companion window's
 * add-or-open button - and a watch has exactly one row by construction (strategic §2 non-goal 2), so
 * the two must agree on every field of it. Owning the shape here rather than at each call site is
 * what makes that structural instead of a convention: a second builder is what let S2034's own phase
 * 01 introduce `wear://watch_data` while the scanner went on matching `wear://watch`.
 */
class EnsureWatchResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val addResourceUseCase: AddResourceUseCase
) {

    /** Which of the two things happened, so the caller can open the row or announce it. */
    data class Outcome(val resourceId: Long, val created: Boolean)

    /**
     * @param name used only when the resource is created; an existing row keeps the name it carries,
     *   because renaming it here would silently overwrite an edit made in the resource editor.
     */
    suspend operator fun invoke(name: String): Result<Outcome> = runCatching {
        val existing = resourceRepository.getAllResources().first()
            .firstOrNull { it.type == ResourceType.WEAR_WATCH }
        if (existing != null) {
            return@runCatching Outcome(existing.id, created = false)
        }
        val createdId = addResourceUseCase(build(name)).getOrThrow()
        Outcome(createdId, created = true)
    }

    private suspend fun build(name: String): MediaResource {
        val settings = settingsRepository.getSettings().first()
        return MediaResource(
            id = 0,
            name = name,
            path = WearWatchResourceStrategy.WATCH_RESOURCE_PATH,
            type = ResourceType.WEAR_WATCH,
            supportedMediaTypes = supportedMediaTypes(settings),
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            // A watch nobody can copy into is not a receiver, and strategic §2 goal 4 asks for exactly
            // that; AddResourceUseCase assigns the order the destination picker filters on.
            isDestination = true,
            destinationOrder = null,
            // Writability is bridge reachability, not a per-folder permission, so it is not probed
            // here: the scanner answers it live and a stale false would refuse every send.
            isWritable = true,
            slideshowInterval = settings.slideshowInterval,
            displayMode = if (settings.defaultGridMode) DisplayMode.GRID else DisplayMode.LIST,
            sortMode = settings.defaultSortMode,
            // The watch exposes one flat downloads directory; there is no tree to recurse into.
            scanSubdirectories = false,
            isReadOnly = false,
            allFiles = settings.allFiles
        )
    }

    /** `allFiles` is the override - honour the shortcut rather than reading the individual toggles. */
    private fun supportedMediaTypes(settings: AppSettings): Set<MediaType> {
        if (settings.allFiles) return MediaType.entries.toSet()
        return buildSet {
            if (settings.supportImages) add(MediaType.IMAGE)
            if (settings.supportVideos) add(MediaType.VIDEO)
            if (settings.supportAudio) add(MediaType.AUDIO)
            if (settings.supportGifs) add(MediaType.GIF)
            if (settings.supportText) add(MediaType.TEXT)
            if (settings.supportPdf) add(MediaType.PDF)
            if (settings.supportEpub) add(MediaType.EPUB)
            if (settings.supportOfficeDocuments) add(MediaType.OFFICE_DOCUMENT)
        }
    }
}
