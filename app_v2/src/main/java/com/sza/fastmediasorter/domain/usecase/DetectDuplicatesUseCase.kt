package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.DuplicateDetectionResult
import com.sza.fastmediasorter.domain.model.DuplicateGroup
import com.sza.fastmediasorter.domain.model.DuplicateScanProgress
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScanPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class DetectDuplicatesUseCase @Inject constructor(
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val computeFileHashUseCase: ComputeFileHashUseCase
) {
    companion object {
        const val QUICK_HASH_BYTES = 4096L
    }

    /**
     * Runs a three-phase duplicate detection scan over [resources].
     * Emits [DuplicateScanProgress] updates; completes with [DuplicateDetectionResult].
     *
     * Phase 0 — group by size (zero I/O).
     * Phase 1 — quick hash (4 KB read per file).
     * Phase 2 — full hash (full file read for phase-1 survivors).
     */
    fun detect(resources: List<MediaResource>): Flow<Any> = flow {
        val startMs = System.currentTimeMillis()

        // --- Phase 0: list all files ---
        emit(DuplicateScanProgress(ScanPhase.LISTING, 0, 0))
        val resourceMap = mutableMapOf<Long, MediaResource>() // resourceId → resource
        val allFiles = mutableListOf<MediaFile>()

        for (resource in resources) {
            if (!coroutineContext.isActive) return@flow
            try {
                val files = getMediaFilesUseCase(resource, forceFullScan = true).first()
                    .filter { it.size > 0 } // exclude zero-byte files
                allFiles.addAll(files)
                resourceMap[resource.id] = resource
            } catch (e: Exception) {
                Timber.w(e, "DetectDuplicatesUseCase: failed to list files for resource ${resource.id}")
            }
        }

        // Group by size; discard singletons
        val bySizeGroups = allFiles.groupBy { it.size }
            .filter { (_, group) -> group.size > 1 }

        val candidateFiles = bySizeGroups.values.flatten()
        val total = candidateFiles.size

        Timber.d("DetectDuplicatesUseCase: ${allFiles.size} total files, $total size-group candidates")
        emit(DuplicateScanProgress(ScanPhase.QUICK_HASH, 0, total))

        // --- Phase 1: quick hash (4 KB) ---
        var processed = 0
        val quickHashGroups = mutableMapOf<String, MutableList<Pair<MediaFile, MediaResource>>>() // quickHash → files

        for ((_, sizeGroup) in bySizeGroups) {
            for (file in sizeGroup) {
                if (!coroutineContext.isActive) return@flow
                val resource = resourceMap[file.resourceId] ?: resources.firstOrNull() ?: continue
                val quickHash = computeFileHashUseCase.compute(file, resource, QUICK_HASH_BYTES)
                processed++
                emit(DuplicateScanProgress(ScanPhase.QUICK_HASH, processed, total))
                if (quickHash != null) {
                    val sizeKey = "${file.size}:$quickHash"
                    quickHashGroups.getOrPut(sizeKey) { mutableListOf() }.add(file to resource)
                }
            }
        }

        // Discard quick-hash-unique entries
        val fullHashCandidates = quickHashGroups.values.filter { it.size > 1 }
        val fullTotal = fullHashCandidates.sumOf { it.size }

        Timber.d("DetectDuplicatesUseCase: $fullTotal full-hash candidates after Phase 1")
        emit(DuplicateScanProgress(ScanPhase.FULL_HASH, 0, fullTotal))

        // --- Phase 2: full hash ---
        var fullProcessed = 0
        val fullHashGroups = mutableMapOf<String, MutableList<MediaFile>>()

        for (group in fullHashCandidates) {
            for ((file, resource) in group) {
                if (!coroutineContext.isActive) return@flow
                val fullHash = computeFileHashUseCase.compute(file, resource, -1L)
                fullProcessed++
                emit(DuplicateScanProgress(ScanPhase.FULL_HASH, fullProcessed, fullTotal))
                if (fullHash != null) {
                    val key = "${file.size}:$fullHash"
                    fullHashGroups.getOrPut(key) { mutableListOf() }.add(file)
                }
            }
        }

        // Build result: only groups with ≥ 2 files
        val groups = fullHashGroups.entries
            .filter { (_, files) -> files.size >= 2 }
            .map { (key, files) ->
                val fullHash = key.substringAfter(":")
                DuplicateGroup(fullHash = fullHash, fileSize = files.first().size, files = files)
            }
            .sortedByDescending { it.fileSize * (it.files.size - 1) }

        val wastedBytes = groups.sumOf { it.fileSize * (it.files.size - 1) }
        val duration = System.currentTimeMillis() - startMs

        emit(DuplicateScanProgress(ScanPhase.DONE, total, total))
        emit(
            DuplicateDetectionResult(
                groups = groups,
                totalFilesScanned = allFiles.size,
                totalWastedBytes = wastedBytes,
                durationMs = duration
            )
        )
        Timber.i("DetectDuplicatesUseCase: found ${groups.size} duplicate groups, ${wastedBytes / 1024} KB wasted, ${duration}ms")
    }
}
