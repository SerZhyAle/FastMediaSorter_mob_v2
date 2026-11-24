package com.sza.fastmediasorter.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.utils.SafHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshPickedTransferCountsUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val refreshResourceFileCounts: RefreshResourceFileCountsUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val refreshMutex = Mutex()

    operator fun invoke(pickedUri: Uri, destinationPath: String, movedFromResourceId: Long?) {
        applicationScope.launch {
            // Counts from concurrent picker transfers must be read and persisted in the same order.
            refreshMutex.withLock {
                val destinationIds = findDestinationIds(pickedUri, destinationPath)
                refreshResourceFileCounts(destinationIds + listOfNotNull(movedFromResourceId))
            }
        }
    }

    private suspend fun findDestinationIds(pickedUri: Uri, destinationPath: String): List<Long> =
        runCatching {
            // Picker registration stores the tree URI even when the transfer writes through a raw path.
            val pickedTree = SafHelper.normalizeContentUri(pickedUri.toString()).trimEnd('/')
            val destinationDirectory = canonicalLocalPath(destinationPath)
            resourceRepository.getResourcesByType(ResourceType.LOCAL).first()
                .filter { resource ->
                    if (SafHelper.isContentUri(resource.path)) {
                        SafHelper.normalizeContentUri(resource.path).trimEnd('/') == pickedTree
                    } else {
                        destinationDirectory != null && canonicalLocalPath(resource.path) == destinationDirectory
                    }
                }
                .map(MediaResource::id)
        }.getOrElse { error ->
            error.warnUnlessCancellation("Picked destination resource lookup failed")
            emptyList()
        }

    private fun canonicalLocalPath(path: String): String? {
        val localPath = if (path.startsWith("file://")) Uri.parse(path).path else path
        val file = localPath?.takeIf { it.startsWith('/') }?.let(::File) ?: return null
        return try {
            file.canonicalPath
        } catch (_: IOException) {
            file.normalize().path
        }
    }
}
