package com.sza.fastmediasorter.ui.main.helpers

import com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.RefreshResourceFileCountsUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import com.sza.fastmediasorter.util.VirtualPathUtils
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject

/**
 * Coordinates comprehensive resource scanning operations.
 * Tests availability, write access, and file counts for all resources.
 * 
 * Responsibilities:
 * - Clear network connection pools before scanning
 * - Test resource connection/availability
 * - Check write permissions
 * - Update file counts (fast scan with limits)
 * - Update resource metadata (availability, lastSyncDate, etc.)
 * - Generate scan summary messages
 *
 * S1424: the constructor is `@Inject` so a second host - the launcher desktop's per-resource menu -
 * can obtain it without assembling its seven dependencies by hand. MainViewModel still builds one
 * itself; an injectable constructor takes nothing away from that.
 */
class ResourceScanCoordinator @Inject constructor(
    private val getResourcesUseCase: GetResourcesUseCase,
    // S0869: Lazy so MainViewModel construction on the main thread does not force provideAppDatabase()
    // through this coordinator. The only use site (testConnection) is inside a suspend fun -> off-main.
    private val resourceRepository: dagger.Lazy<ResourceRepository>,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val mediaScannerFactory: MediaScannerFactory,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val remoteSourceGate: RemoteSourceAvailabilityGate,
    private val refreshResourceFileCountsUseCase: RefreshResourceFileCountsUseCase
) {
    
    /**
     * Result of full resource scan.
     */
    data class ScanResult(
        val totalResources: Int,
        val availableCount: Int,
        val unavailableCount: Int,
        val writableCount: Int,
        val readOnlyCount: Int
    ) {
        /**
         * Generate user-friendly summary message.
         * Returns string resource ID to be used with getString() in Activity/Fragment.
         */
        fun getSummaryMessageResId(): Int {
            return when {
                unavailableCount == 0 -> com.sza.fastmediasorter.R.string.all_resources_available
                unavailableCount == totalResources -> com.sza.fastmediasorter.R.string.all_resources_unavailable
                else -> com.sza.fastmediasorter.R.string.resources_checked
            }
        }
        
        /**
         * Get format arguments for string resource.
         */
        fun getSummaryMessageArgs(): Array<Any> {
            return when {
                unavailableCount == 0 -> arrayOf(writableCount, readOnlyCount)
                unavailableCount == totalResources -> emptyArray()
                else -> arrayOf(availableCount, unavailableCount)
            }
        }
    }
    
    /**
     * Result of a single-resource scan operation (S0160).
     */
    sealed class SingleScanResult {
        data class Available(val resource: MediaResource) : SingleScanResult()
        data class Unavailable(val resource: MediaResource) : SingleScanResult()
    }

    /**
     * Scan a single resource and return its availability result (S0160).
     * Calls the existing private [scanSingleResource] which updates the DB via [updateResourceUseCase].
     */
    suspend fun scanAndRefreshSingleResource(resource: MediaResource): SingleScanResult {
        return try {
            val isWritable = scanSingleResource(resource)
            if (isWritable != null) SingleScanResult.Available(resource)
            else SingleScanResult.Unavailable(resource)
        } catch (e: Exception) {
            Timber.w(e, "Single resource scan failed: ${resource.name}")
            SingleScanResult.Unavailable(resource)
        }
    }

    /**
     * Check if any resources are aggregate virtual paths (All Music, All Videos, All Documents).
     * Used to show a warning dialog before mass rescan.
     */
    fun hasAggregateVirtualResources(resources: List<MediaResource>): Boolean {
        return resources.any { VirtualPathUtils.isAggregateVirtualPath(it.path) }
    }

    /**
     * Scan all resources: test availability, write access, and update file counts.
     * This is a comprehensive operation that updates resource metadata.
     * 
     * @return ScanResult with scan statistics
     */
    suspend fun scanAllResources(): ScanResult {
        // Clear all network connection pools to avoid stale/blocked connections
        Timber.d("Clearing network connection pools before resource scan")
        smbOperationsUseCase.clearAllConnectionPools()
        
        // S0391: never connection-test or count a disabled source's resources - they are inert.
        val resources = getResourcesUseCase().first().filter { remoteSourceGate.isEnabled(it) }
        Timber.d("Starting scan of ${resources.size} resources")
        
        var unavailableCount = 0
        var writableCount = 0
        var readOnlyCount = 0
        
        resources.forEachIndexed { index, resource ->
            Timber.d("Scanning resource [${index + 1}/${resources.size}]: ${resource.name} (${resource.type})")
            
            try {
                scanSingleResource(resource)?.let { isWritable ->
                    if (isWritable) writableCount++ else readOnlyCount++
                } ?: run {
                    unavailableCount++
                }
            } catch (e: Exception) {
                Timber.w(e, "Resource check failed: ${resource.name}")
                unavailableCount++
                
                // Update availability to false on exception
                if (resource.isAvailable) {
                    val updatedResource = resource.copy(isAvailable = false)
                    updateResourceUseCase(updatedResource)
                }
            }
        }
        
        Timber.d("Resource scan completed: ${resources.size} total")
        val availableCount = resources.size - unavailableCount
        
        return ScanResult(
            totalResources = resources.size,
            availableCount = availableCount,
            unavailableCount = unavailableCount,
            writableCount = writableCount,
            readOnlyCount = readOnlyCount
        )
    }
    
    /**
     * Scan single resource. Returns write status if available, null if unavailable.
     */
    private suspend fun scanSingleResource(resource: MediaResource): Boolean? {
        // Virtual resources are always available and never writable - skip testConnection/isWritable
        if (VirtualPathUtils.isVirtualPath(resource.path)) {
            return processVirtualResource(resource)
        }

        // Test connection/availability
        Timber.d("Testing connection for ${resource.name}...")
        val testResult = resourceRepository.get().testConnection(resource)
        Timber.d("Connection test completed for ${resource.name}")
        
        return testResult.fold(
            onSuccess = { message ->
                Timber.d("Resource available: ${resource.name}")
                processAvailableResource(resource, message)
            },
            onFailure = { error ->
                Timber.w("Resource unavailable: ${resource.name} - ${error.message}")
                
                // Update availability to false
                if (resource.isAvailable) {
                    val updatedResource = resource.copy(isAvailable = false)
                    updateResourceUseCase(updatedResource)
                }
                null
            }
        )
    }
    
    /**
     * Process virtual resource: skip connection and write checks, then refresh its stored count.
     * Virtual resources are always available and never writable.
     */
    private suspend fun processVirtualResource(resource: MediaResource): Boolean {
        if (!resource.isAvailable) {
            updateResourceUseCase(resource.copy(isAvailable = true))
        }
        refreshResourceFileCountsUseCase(setOf(resource.id))
        return false // Virtual resources are never writable
    }

    /**
     * Process available resource: check write access and update file count.
     * Returns write status.
     */
    private suspend fun processAvailableResource(resource: MediaResource, testConnectionMessage: String): Boolean {
        var needsUpdate = false
        var updatedResource = resource
        
        // Parse subfolder count from testConnection message
        val subfolderCount = parseSubfolderCount(testConnectionMessage)
        if (subfolderCount != resource.subfolderCount) {
            updatedResource = updatedResource.copy(subfolderCount = subfolderCount)
            needsUpdate = true
        }
        
        // Update availability to true
        if (!resource.isAvailable) {
            updatedResource = updatedResource.copy(isAvailable = true)
            needsUpdate = true
        }
        
        // Update lastSyncDate for network resources
        val isNetworkResource = resource.type == ResourceType.SMB || 
                                resource.type == ResourceType.SFTP || 
                                resource.type == ResourceType.FTP
        if (isNetworkResource) {
            updatedResource = updatedResource.copy(lastSyncDate = System.currentTimeMillis())
            needsUpdate = true
        }
        
        val scanner = mediaScannerFactory.getScanner(resource.type)
        Timber.d("Checking write access for ${resource.name}...")
        val isWritable = try {
            scanner.isWritable(resource.path, resource.credentialsId)
        } catch (e: Exception) {
            Timber.e(e, "Error checking write access for ${resource.name}")
            resource.isWritable
        }
        Timber.d("Write access check completed for ${resource.name}: $isWritable")
        
        // Update resource if write permission changed
        if (isWritable != resource.isWritable) {
            updatedResource = updatedResource.copy(isWritable = isWritable)
            needsUpdate = true
        }
        
        if (needsUpdate) {
            updateResourceUseCase(updatedResource)
        }
        refreshResourceFileCountsUseCase(setOf(resource.id))
        return isWritable
    }
    
    /**
     * Parse subfolder count from testConnection success message.
     * Message format: "• Subfolders: 35"
     * 
     * @return Parsed count or 0 if not found
     */
    private fun parseSubfolderCount(message: String): Int {
        return try {
            val regex = """Subfolders:\s*(\d+)""".toRegex()
            regex.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse subfolder count from message: $message")
            0
        }
    }
}
