package com.sza.fastmediasorter.ui.addresource

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Shared post-insert finalization for network resources.
 *
 * After an SMB/SFTP/FTP resource is inserted we need to:
 *   1. Re-scan to populate fileCount and isWritable.
 *   2. Persist the scanned values back into the DB.
 *   3. Trigger a background speed test for accessible resources.
 *
 * The SMB/SFTP/SFTP-key coordinators all share this pattern — duplicating it
 * across five call sites masked subtle drift (write-test skip, thumbnail
 * auto-disable threshold, error messaging). This helper keeps it in one place.
 */
internal class AddResourceFinalizer(
    private val bridge: AddResourceBridge,
    private val resourceRepository: ResourceRepository,
    private val mediaScannerFactory: MediaScannerFactory
) {

    /**
     * Scans a freshly-inserted resource, updates the DB row, and triggers a speed test
     * for accessible resources. Returns true if the scan succeeded.
     *
     * @param resource        the in-memory resource that was just inserted (id may be 0).
     * @param credentialsId   credentials id attached on insert — used to locate the real row.
     * @param skipWriteTest   when true, skips isWritable probe (read-only resources).
     * @param onlyTestIfWritable when true, speed test is triggered only if isWritable=true
     *                        (SMB manual-add path: read-only can't create .speedtest_*.tmp).
     */
    suspend fun scanInsertedResource(
        resource: MediaResource,
        credentialsId: String?,
        skipWriteTest: Boolean = false,
        onlyTestIfWritable: Boolean = false
    ): Boolean {
        var scanSuccessful = false
        bridge.vmScope.launch(bridge.ioDispatcher) {
            try {
                val scanner = mediaScannerFactory.getScanner(resource.type)
                val mediaTypes = bridge.supportedMediaTypes()

                val fileCount = scanner.getFileCount(
                    resource.path,
                    mediaTypes,
                    sizeFilter = null,
                    credentialsId = resource.credentialsId,
                    scanSubdirectories = resource.scanSubdirectories
                )
                val isWritable = if (skipWriteTest) {
                    false
                } else {
                    withTimeout(5000) {
                        scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                    }
                }

                val currentResources = resourceRepository.getAllResources().first()
                val insertedResource = currentResources.firstOrNull {
                    it.path == resource.path && it.credentialsId == credentialsId
                }
                if (insertedResource == null) {
                    Timber.e("Failed to find inserted resource in database: path=${resource.path}")
                    return@launch
                }

                val updatedResource = insertedResource.copy(
                    fileCount = fileCount,
                    isWritable = isWritable,
                    // auto-enable thumbnail suppression for huge folders, preserve existing flag
                    disableThumbnails = insertedResource.disableThumbnails || fileCount > 10000
                )
                resourceRepository.updateResource(updatedResource)
                Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                scanSuccessful = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to scan resource ${resource.name}")
            }
        }.join()

        if (scanSuccessful) {
            bridge.appScope.launch(bridge.ioDispatcher) {
                val allResources = resourceRepository.getAllResources().first()
                val inserted = allResources.firstOrNull {
                    it.path == resource.path && it.credentialsId == credentialsId
                }
                if (inserted != null && (!onlyTestIfWritable || inserted.isWritable)) {
                    bridge.runSpeedTest(inserted)
                }
            }
        }
        return scanSuccessful
    }

    /**
     * Bulk variant used by SMB share scan — inserts already happened, this walks each
     * in-memory resource and updates its DB row. Returns unavailable count.
     */
    suspend fun scanInsertedResources(
        resources: List<MediaResource>,
        credentialsId: String
    ): Int {
        var unavailableCount = 0
        bridge.vmScope.launch(bridge.ioDispatcher) {
            val currentResources = resourceRepository.getAllResources().first()
            resources.forEach { resource ->
                try {
                    val insertedResource = currentResources.firstOrNull {
                        it.path == resource.path && it.credentialsId == credentialsId
                    }
                    if (insertedResource == null) {
                        Timber.e("Failed to find inserted resource ${resource.name} in database")
                        unavailableCount++
                        return@forEach
                    }

                    val scanner = mediaScannerFactory.getScanner(resource.type)
                    val mediaTypes = bridge.supportedMediaTypes()
                    val fileCount = scanner.getFileCount(
                        resource.path,
                        mediaTypes,
                        sizeFilter = null,
                        credentialsId = resource.credentialsId,
                        scanSubdirectories = resource.scanSubdirectories
                    )
                    val isWritable = withTimeout(5000) {
                        scanner.isWritable(resource.path, credentialsId = resource.credentialsId)
                    }

                    val updatedResource = insertedResource.copy(
                        fileCount = fileCount,
                        isWritable = isWritable,
                        disableThumbnails = insertedResource.disableThumbnails || fileCount > 10000
                    )
                    resourceRepository.updateResource(updatedResource)
                    Timber.d("Scanned ${resource.name}: $fileCount files, writable=$isWritable")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to scan resource ${resource.name}")
                    unavailableCount++
                }
            }
        }.join()

        bridge.appScope.launch(bridge.ioDispatcher) {
            val allResources = resourceRepository.getAllResources().first()
            resources.forEach { resource ->
                val inserted = allResources.firstOrNull {
                    it.path == resource.path && it.credentialsId == credentialsId
                }
                if (inserted != null && inserted.isWritable) {
                    bridge.runSpeedTest(inserted)
                }
            }
        }
        return unavailableCount
    }

    /**
     * Computes destination slot (order + color) if the user requested one and capacity remains.
     * Returns null if the caller should surface an error and abort.
     *
     * @return Triple(isDestination, destinationOrder, destinationColor), or null on overflow.
     */
    suspend fun allocateDestinationSlot(
        addToDestinations: Boolean,
        isReadOnly: Boolean
    ): Triple<Boolean, Int, Int>? {
        if (!addToDestinations || isReadOnly) return Triple(false, 0, 0)

        val allResources = resourceRepository.getAllResources().first()
        val destinations = allResources.filter { it.isDestination }
        if (destinations.size >= 10) {
            bridge.emit(AddResourceEvent.ShowError("Maximum 30 destinations allowed"))
            bridge.markLoading(false)
            return null
        }
        val maxOrder = destinations.mapNotNull { it.destinationOrder }.maxOrNull() ?: -1
        val nextOrder = maxOrder + 1
        val color = com.sza.fastmediasorter.core.util.DestinationColors.getColorForDestination(nextOrder)
        return Triple(true, nextOrder, color)
    }
}

internal object AddResourceTypes {
    /** Protocol families that should receive a post-insert speed test. */
    val networkResourceTypes = setOf(
        ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP, ResourceType.CLOUD
    )
}
