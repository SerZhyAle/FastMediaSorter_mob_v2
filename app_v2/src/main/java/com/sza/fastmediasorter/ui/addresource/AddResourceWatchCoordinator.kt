package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.strategy.WearWatchResourceStrategy
import com.sza.fastmediasorter.domain.usecase.AddResourceUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1861: adds the paired watch as a resource.
 *
 * The shortest of the coordinators on purpose: a watch has no address and no credentials, so the
 * wizard asks for a name and nothing else. There is exactly one paired watch, so a second resource
 * would point at the same device - an existing one is reported rather than duplicated.
 */
internal class AddResourceWatchCoordinator(
    private val context: Context,
    private val addResourceUseCase: AddResourceUseCase,
    private val resourceRepository: ResourceRepository,
    private val settingsRepository: SettingsRepository,
    private val bridge: AddResourceBridge
) {

    fun addPairedWatch(name: String) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.markLoading(true)
            try {
                val existing = resourceRepository.getAllResources().first()
                    .firstOrNull { it.type == ResourceType.WEAR_WATCH }
                if (existing == null) {
                    insert(name)
                } else {
                    bridge.emit(
                        AddResourceEvent.ShowMessage(context.getString(R.string.paired_watch_already_added))
                    )
                }
            } finally {
                bridge.markLoading(false)
            }
        }
    }

    private suspend fun insert(name: String) {
        val settings = settingsRepository.getSettings().first()
        val resource = MediaResource(
            id = 0,
            name = name.ifBlank { context.getString(R.string.resource_type_wear_watch) },
            path = WearWatchResourceStrategy.WATCH_RESOURCE_PATH,
            type = ResourceType.WEAR_WATCH,
            supportedMediaTypes = bridge.supportedMediaTypes(),
            createdDate = System.currentTimeMillis(),
            fileCount = 0,
            // A watch nobody can copy into is not a receiver, and goal 2 asks for exactly that;
            // AddResourceUseCase assigns the order the destination picker filters on.
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
        val createdId = addResourceUseCase(resource).getOrNull()
        if (createdId == null) {
            Timber.w("Failed to insert the paired watch resource")
            bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.friendly_copy_error_generic)))
        } else {
            bridge.emit(
                AddResourceEvent.ShowMessage(
                    context.getString(R.string.paired_watch_resource_added, resource.name)
                )
            )
            bridge.emit(AddResourceEvent.ResourcesAdded(listOf(createdId)))
        }
    }
}
