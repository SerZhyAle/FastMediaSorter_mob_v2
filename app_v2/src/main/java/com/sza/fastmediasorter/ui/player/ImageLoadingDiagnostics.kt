package com.sza.fastmediasorter.ui.player

import com.bumptech.glide.load.engine.GlideException
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber

/**
 * Pure diagnostic helpers for ImageLoadingManager: classify Glide network-image failures as
 * non-critical (so they're not surfaced to the user as errors), and emit a one-line
 * heap/native-heap/preload-job snapshot for memory-pressure tracking.
 *
 * Extracted to keep ImageLoadingManager below the 1000-line cap.
 */
object ImageLoadingDiagnostics {

    /**
     * True when [exception] originates from network image loading (NetworkFileDataFetcher /
     * smb/sftp/ftp paths) AND the root cause is a benign decode failure (corrupted data,
     * cancelled request, decode/HEIC/HEIF/AVIF). These are common transient failures during
     * fast scrolling and shouldn't be surfaced as errors.
     */
    fun isNonCriticalNetworkImageError(exception: GlideException?): Boolean {
        if (exception == null) return false

        val messages = buildList {
            add(exception.message ?: "")
            exception.rootCauses.forEach { rootCause ->
                add(rootCause.message ?: "")
                var cause = rootCause.cause
                while (cause != null) {
                    add(cause.message ?: "")
                    cause = cause.cause
                }
            }
        }

        val hasNetworkContext = messages.any { msg ->
            msg.contains("NetworkFileDataFetcher", ignoreCase = true) ||
                msg.contains("Failed to load network file:", ignoreCase = true) ||
                msg.contains("smb://", ignoreCase = true) ||
                msg.contains("sftp://", ignoreCase = true) ||
                msg.contains("ftp://", ignoreCase = true)
        }

        if (!hasNetworkContext) return false

        return messages.any { msg ->
            msg.contains("Invalid/corrupted image data", ignoreCase = true) ||
                msg.contains("Corrupted image data:", ignoreCase = true) ||
                msg.contains("Request cancelled", ignoreCase = true) ||
                msg.contains("Failed to decode", ignoreCase = true) ||
                msg.contains("DecodeException", ignoreCase = true) ||
                msg.contains("ImageDecoder", ignoreCase = true) ||
                msg.contains("HEIC", ignoreCase = true) ||
                msg.contains("HEIF", ignoreCase = true) ||
                msg.contains("AVIF", ignoreCase = true)
        }
    }

    /**
     * Log a single MEMORY_DEBUG line with heap/native usage and active preload-job count.
     * Call before/after image loading to track memory consumption patterns.
     */
    fun logMemoryStats(context: String, activePreloadJobs: Int) {
        // S0207 Phase 01: gate behind DEBUG - not yet routed through MemoryProbe; gating
        // prevents release-build computation overhead and keeps parity with MemoryProbeImpl.
        if (!BuildConfig.DEBUG) return
        try {
            val runtime = Runtime.getRuntime()

            // Heap memory stats (in MB)
            val totalMemory = runtime.totalMemory() / (1024 * 1024)
            val freeMemory = runtime.freeMemory() / (1024 * 1024)
            val maxMemory = runtime.maxMemory() / (1024 * 1024)
            val usedMemory = totalMemory - freeMemory
            val percentUsed = (usedMemory * 100) / maxMemory

            val nativeHeapSize = android.os.Debug.getNativeHeapSize() / (1024 * 1024)
            val nativeHeapAllocated = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val nativeHeapFree = android.os.Debug.getNativeHeapFreeSize() / (1024 * 1024)

            Timber.i(
                "MEMORY_DEBUG [$context] | " +
                    "Heap: ${usedMemory}MB/${maxMemory}MB (${percentUsed}%) | " +
                    "Native: ${nativeHeapAllocated}MB/${nativeHeapSize}MB (free: ${nativeHeapFree}MB) | " +
                    "Preload Jobs: $activePreloadJobs"
            )
        } catch (e: Exception) {
            Timber.w(e, "MEMORY_DEBUG: Failed to log memory stats")
        }
    }
}
