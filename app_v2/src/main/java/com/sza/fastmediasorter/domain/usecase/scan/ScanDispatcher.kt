package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces per-source-type concurrency limits for scan operations (A5-T9, A5-T10).
 *
 * Wraps a [Semaphore] per [ResourceType] category. Call [withScanPermit] inside
 * any scan coroutine to wait for an available slot before proceeding.
 *
 * Thread-safe. Singletons survive across scan cycles - [reset] must be called
 * explicitly if [ScanSettings] limits are changed at runtime.
 *
 * Usage:
 * ```
 * scanDispatcher.withScanPermit(resource.type) {
 *     fileScanner.scanFolder(resource)
 * }
 * ```
 */
@Singleton
class ScanDispatcher @Inject constructor(
    private val settings: ScanSettings
) {
    // One semaphore per logical source category, lazily initialised.
    @Volatile
    private var localSemaphore    = Semaphore(settings.localParallelism)

    @Volatile
    private var networkSemaphore  = Semaphore(settings.networkParallelism)

    @Volatile
    private var cloudSemaphore    = Semaphore(settings.cloudParallelism)

    /**
     * Acquires a scan permit for [type], executes [block], then releases the permit.
     * Suspends if all permits for this category are in use.
     */
    suspend fun <T> withScanPermit(type: ResourceType, block: suspend () -> T): T {
        val semaphore = semaphoreFor(type)
        Timber.v("ScanDispatcher: acquiring permit for $type (available=${semaphore.availablePermits})")
        return semaphore.withPermit {
            Timber.v("ScanDispatcher: permit acquired for $type")
            try {
                block()
            } finally {
                Timber.v("ScanDispatcher: permit released for $type")
            }
        }
    }

    /**
     * Returns the number of available permits for a given [ResourceType].
     * Useful for diagnostics and logging.
     */
    fun availablePermits(type: ResourceType): Int = semaphoreFor(type).availablePermits

    /**
     * Re-initialises all semaphores with current [ScanSettings] values.
     * Call ONLY when no scans are in progress - existing permits are dropped.
     */
    fun reset() {
        Timber.d(
            "ScanDispatcher: resetting - local=${settings.localParallelism}, " +
                "network=${settings.networkParallelism}, cloud=${settings.cloudParallelism}"
        )
        localSemaphore   = Semaphore(settings.localParallelism)
        networkSemaphore = Semaphore(settings.networkParallelism)
        cloudSemaphore   = Semaphore(settings.cloudParallelism)
    }

    private fun semaphoreFor(type: ResourceType): Semaphore = when (type) {
        ResourceType.LOCAL          -> localSemaphore
        ResourceType.SMB,
        ResourceType.SFTP,
        ResourceType.FTP            -> networkSemaphore
        ResourceType.CLOUD          -> cloudSemaphore
    }
}
