package com.sza.fastmediasorter.domain.model

/** One group of files with identical content (same fileSize + fullHash). */
data class DuplicateGroup(
    val fullHash: String,       // MD5 hex of full file content
    val fileSize: Long,         // Byte size (same for all members)
    val files: List<MediaFile>  // ≥ 2 members
)

/** Result of a completed duplicate detection run. */
data class DuplicateDetectionResult(
    val groups: List<DuplicateGroup>,
    val totalFilesScanned: Int,
    val totalWastedBytes: Long, // Sum of (files.size - 1) * fileSize per group
    val durationMs: Long
)

/** Progress update emitted during a scan. */
data class DuplicateScanProgress(
    val phase: ScanPhase,
    val filesProcessed: Int,
    val totalFiles: Int
)

enum class ScanPhase { LISTING, QUICK_HASH, FULL_HASH, DONE }
