package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.logging.CorrelationContext
import com.sza.fastmediasorter.core.logging.StructuredLogger
import com.sza.fastmediasorter.core.metrics.OperationMetricsRecorder
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.utils.SftpPathUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AddMultipleResult(
    val addedCount: Int,
    val destinationsFull: Boolean,
    val skippedDestinations: Int,
    // S1012: companion add-or-update outcome. updatedCount is diff-based (a byte-identical re-import
    // stays 0). Names carry the affected resources so the caller can name the single-affected case.
    val updatedCount: Int = 0,
    val addedNames: List<String> = emptyList(),
    val updatedNames: List<String> = emptyList()
)

class AddResourceUseCase @Inject constructor(
    private val repository: ResourceRepository
) {
    companion object {
        const val MAX_DESTINATIONS = 10
    }
    
    suspend operator fun invoke(resource: MediaResource, addToTop: Boolean = false): Result<Long> = withContext(CorrelationContext.asContextElement("add-resource")) {
        try {
            StructuredLogger.d(
                "START add resource",
                "name" to resource.name,
                "type" to resource.type.name,
                "addToTop" to addToTop
            )
            val existingResources = normalizeDisplayOrder(repository.getAllResources().first())
            val resourceWithOrder = if (addToTop) {
                existingResources.forEachIndexed { index, existing ->
                    repository.updateResource(existing.copy(displayOrder = index + 1))
                }
                resource.copy(displayOrder = 0)
            } else {
                resource.copy(displayOrder = existingResources.size)
            }
            
            val id = repository.addResource(resourceWithOrder)
            StructuredLogger.i("SUCCESS add resource", "id" to id)
            OperationMetricsRecorder.recordResourceSave(success = true)
            Result.success(id)
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE add resource")
            OperationMetricsRecorder.recordResourceSave(success = false)
            Result.failure(e)
        }
    }

    /**
     * Adds a batch of resources. When [matchExistingByPath] is true (companion import only, S1012),
     * an incoming resource whose primary path matches an already-stored resource of the same type is
     * merged over the existing row instead of inserted, so a repeated companion import updates in
     * place rather than creating duplicates. Only config-authoritative fields are overwritten;
     * user/runtime fields are preserved (see [mergeConfigAuthoritative]). "Updated" is diff-based -
     * a byte-identical re-import writes nothing and counts zero. The default (false) keeps the
     * insert-only behaviour every other call site relies on.
     */
    suspend fun addMultiple(
        resources: List<MediaResource>,
        matchExistingByPath: Boolean = false
    ): Result<AddMultipleResult> = withContext(
        CorrelationContext.asContextElement("add-multiple-resources")
    ) {
        try {
            StructuredLogger.d("START add multiple", "count" to resources.size, "matchByPath" to matchExistingByPath)
            val existingResources = normalizeDisplayOrder(repository.getAllResources().first())

            val updatedNames = mutableListOf<String>()
            val toInsert = if (matchExistingByPath) {
                partitionAndUpdateMatches(resources, existingResources, updatedNames)
            } else {
                resources
            }

            val currentDestinations = existingResources.count { it.isDestination }
            var availableDestinationSlots = MAX_DESTINATIONS - currentDestinations
            // Use -1 as initial value so first destination gets order 0 after increment
            // Treat null destinationOrder as -1 to ensure first destination gets order 0
            var nextDestinationOrder = existingResources
                .filter { it.isDestination }
                .maxOfOrNull { it.destinationOrder ?: -1 } ?: -1

            // Calculate max displayOrder for new resources
            var nextDisplayOrder = existingResources.size - 1

            var skippedDestinations = 0
            val resourcesToAdd = toInsert.map { resource ->
                nextDisplayOrder++

                if (resource.isDestination && availableDestinationSlots > 0) {
                    nextDestinationOrder++
                    availableDestinationSlots--
                    val color = DestinationColors.getColorForDestination(nextDestinationOrder)
                    resource.copy(
                        destinationOrder = nextDestinationOrder,
                        destinationColor = color,
                        displayOrder = nextDisplayOrder
                    )
                } else if (resource.isDestination && availableDestinationSlots <= 0) {
                    skippedDestinations++
                    resource.copy(isDestination = false, destinationOrder = null, displayOrder = nextDisplayOrder)
                } else {
                    resource.copy(displayOrder = nextDisplayOrder)
                }
            }

            resourcesToAdd.forEach { repository.addResource(it) }

            StructuredLogger.i(
                "SUCCESS add multiple",
                "added" to resourcesToAdd.size,
                "updated" to updatedNames.size,
                "skipped" to skippedDestinations
            )
            Result.success(
                AddMultipleResult(
                    addedCount = resourcesToAdd.size,
                    destinationsFull = skippedDestinations > 0,
                    skippedDestinations = skippedDestinations,
                    updatedCount = updatedNames.size,
                    addedNames = resourcesToAdd.map { it.name },
                    updatedNames = updatedNames
                )
            )
        } catch (e: Exception) {
            StructuredLogger.e(e, "FAILURE add multiple")
            Result.failure(e)
        }
    }

    /**
     * S1012: updates every incoming resource that matches an existing same-type resource by path and
     * returns the resources that had no match and must be inserted. A match is written only when the
     * config-authoritative merge actually changes a field (diff-based); the changed resource's name
     * is appended to [updatedNames]. Runs in memory over the already-loaded [existingResources].
     */
    private suspend fun partitionAndUpdateMatches(
        incoming: List<MediaResource>,
        existingResources: List<MediaResource>,
        updatedNames: MutableList<String>
    ): List<MediaResource> {
        val toInsert = mutableListOf<MediaResource>()
        incoming.forEach { resource ->
            val match = existingResources.firstOrNull {
                it.type == resource.type && pathsMatch(it.path, resource.path)
            }
            if (match == null) {
                toInsert += resource
            } else {
                val merged = mergeConfigAuthoritative(match, resource)
                if (merged != match) {
                    repository.updateResource(merged)
                    updatedNames += merged.name
                }
            }
        }
        return toInsert
    }

    /**
     * S1012: config-authoritative selective merge. Overwrites only the fields a companion config
     * owns and preserves every user/runtime field (id, name, icon, sort/display mode, view state,
     * stats, displayOrder, destination slot/color, comment, pin, profile). Building from
     * [existing].copy avoids the full-row clobber documented on ResourceRepository (S1001).
     */
    private fun mergeConfigAuthoritative(existing: MediaResource, incoming: MediaResource): MediaResource =
        existing.copy(
            path = incoming.path,
            credentialsId = incoming.credentialsId,
            hostKeyFingerprint = incoming.hostKeyFingerprint,
            supportedMediaTypes = incoming.supportedMediaTypes,
            allFiles = incoming.allFiles,
            scanSubdirectories = incoming.scanSubdirectories,
            showHiddenFiles = incoming.showHiddenFiles,
            showSubfoldersAsItems = incoming.showSubfoldersAsItems,
            isReadOnly = incoming.isReadOnly,
            altAccessPaths = incoming.altAccessPaths,
            accessNote = incoming.accessNote
        )

    /**
     * S1012: primary-path identity within one resource type. For SFTP (the companion type) the key
     * is host + virtualPath: the port is ignored and a trailing slash on the path is stripped, the
     * comparison is case-sensitive. Non-SFTP paths fall back to exact equality.
     */
    private fun pathsMatch(existingPath: String, incomingPath: String): Boolean {
        val a = SftpPathUtils.parseSftpPath(existingPath)
        val b = SftpPathUtils.parseSftpPath(incomingPath)
        return if (a != null && b != null) {
            a.host == b.host && stripTrailingSlash(a.remotePath) == stripTrailingSlash(b.remotePath)
        } else {
            existingPath == incomingPath
        }
    }

    private fun stripTrailingSlash(path: String): String {
        val trimmed = path.trimEnd('/')
        return trimmed.ifEmpty { "/" }
    }

    private suspend fun normalizeDisplayOrder(existingResources: List<MediaResource>): List<MediaResource> {
        val ordered = existingResources.sortedBy { it.displayOrder }
        ordered.forEachIndexed { index, resource ->
            if (resource.displayOrder != index) {
                repository.updateResource(resource.copy(displayOrder = index))
            }
        }
        return ordered.mapIndexed { index, resource ->
            if (resource.displayOrder == index) resource else resource.copy(displayOrder = index)
        }
    }
}
