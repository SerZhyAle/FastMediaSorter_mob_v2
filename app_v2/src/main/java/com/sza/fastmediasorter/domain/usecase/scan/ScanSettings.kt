package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.domain.model.ResourceType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configurable parallelism limits for scan operations by source type (A5-T11).
 *
 * These values cap the number of concurrent scan coroutines per [ResourceType] category:
 * - LOCAL: abundant CPU/IO bandwidth, allow more parallelism.
 * - NETWORK (SMB/SFTP/FTP): limited by remote server capacity.
 * - CLOUD: strict API rate limits, keep to 1.
 *
 * Limits are enforced by [ScanDispatcher] via Semaphore.
 * Changing these at runtime requires restarting the dispatcher (call [ScanDispatcher.reset]).
 */
@Singleton
class ScanSettings @Inject constructor() {

    /** Maximum simultaneous scans of LOCAL resources. */
    var localParallelism: Int = DEFAULT_LOCAL_PARALLELISM
        set(value) {
            require(value in 1..MAX_PARALLELISM) {
                "localParallelism must be between 1 and $MAX_PARALLELISM, got $value"
            }
            field = value
        }

    /** Maximum simultaneous scans of NETWORK resources (SMB / SFTP / FTP). */
    var networkParallelism: Int = DEFAULT_NETWORK_PARALLELISM
        set(value) {
            require(value in 1..MAX_PARALLELISM) {
                "networkParallelism must be between 1 and $MAX_PARALLELISM, got $value"
            }
            field = value
        }

    /** Maximum simultaneous scans of CLOUD resources (Google Drive / OneDrive / Dropbox). */
    var cloudParallelism: Int = DEFAULT_CLOUD_PARALLELISM
        set(value) {
            require(value in 1..MAX_PARALLELISM) {
                "cloudParallelism must be between 1 and $MAX_PARALLELISM, got $value"
            }
            field = value
        }

    /** Maps a [ResourceType] to the configured concurrency limit for that category. */
    fun limitFor(type: ResourceType): Int = when (type) {
        ResourceType.LOCAL -> localParallelism
        ResourceType.SMB,
        ResourceType.SFTP,
        ResourceType.FTP -> networkParallelism
        ResourceType.CLOUD,
        ResourceType.HTTP_STREAM,
        ResourceType.RTSP_STREAM -> cloudParallelism
    }

    companion object {
        const val DEFAULT_LOCAL_PARALLELISM = 4
        const val DEFAULT_NETWORK_PARALLELISM = 2
        const val DEFAULT_CLOUD_PARALLELISM = 1
        const val MAX_PARALLELISM = 8
    }
}
