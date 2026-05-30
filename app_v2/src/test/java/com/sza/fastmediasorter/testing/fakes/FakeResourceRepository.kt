package com.sza.fastmediasorter.testing.fakes

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written fake for [ResourceRepository].
 *
 * Backing state is an in-memory list exposed via [resources]; reactive reads observe
 * [resourcesFlow]. Mutating helpers keep both in sync. Invocations that a test typically asserts on
 * are recorded in the `*Calls` / `deleted*` collections. No real I/O, no Android, no logging.
 */
class FakeResourceRepository : ResourceRepository {

    /** Current resource set; mutate directly or via the repository methods. */
    val resources: MutableList<MediaResource> = mutableListOf()

    private val resourcesFlow = MutableStateFlow<List<MediaResource>>(emptyList())

    /** Result returned by [testConnection]; override per test. */
    var testConnectionResult: Result<String> = Result.success("OK")

    // Invocation records ---------------------------------------------------
    val addedResources: MutableList<MediaResource> = mutableListOf()
    val updatedResources: MutableList<MediaResource> = mutableListOf()
    val deletedResourceIds: MutableList<Long> = mutableListOf()
    val testedConnections: MutableList<MediaResource> = mutableListOf()
    var deleteAllCalled: Boolean = false
    var nextInsertId: Long = 1L

    /** Replace the backing set and refresh the flow in one shot. */
    fun setResources(items: List<MediaResource>) {
        resources.clear()
        resources.addAll(items)
        publish()
    }

    private fun publish() {
        resourcesFlow.value = resources.toList()
    }

    override fun getAllResources(): Flow<List<MediaResource>> = resourcesFlow

    override suspend fun getAllResourcesSync(): List<MediaResource> = resources.toList()

    override suspend fun getResourceById(id: Long): MediaResource? =
        resources.firstOrNull { it.id == id }

    override fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>> =
        MutableStateFlow(resources.filter { it.type == type })

    override fun getDestinations(): Flow<List<MediaResource>> =
        MutableStateFlow(resources.filter { it.isDestination })

    override suspend fun getFilteredResources(
        filterByType: Set<ResourceType>?,
        filterByMediaType: Set<MediaType>?,
        filterByName: String?,
        sortMode: SortMode,
    ): List<MediaResource> = resources.filter { resource ->
        (filterByType == null || resource.type in filterByType) &&
            (filterByMediaType == null || resource.supportedMediaTypes.any { it in filterByMediaType }) &&
            (filterByName == null ||
                resource.name.contains(filterByName, ignoreCase = true) ||
                resource.path.contains(filterByName, ignoreCase = true))
    }

    override suspend fun addResource(resource: MediaResource): Long {
        addedResources.add(resource)
        val id = if (resource.id != 0L) resource.id else nextInsertId++
        resources.add(resource.copy(id = id))
        publish()
        return id
    }

    override suspend fun updateResource(resource: MediaResource) {
        updatedResources.add(resource)
        val index = resources.indexOfFirst { it.id == resource.id }
        if (index >= 0) {
            resources[index] = resource
        }
        publish()
    }

    override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) {
        val i1 = resources.indexOfFirst { it.id == resource1.id }
        val i2 = resources.indexOfFirst { it.id == resource2.id }
        if (i1 >= 0 && i2 >= 0) {
            val order1 = resources[i1].displayOrder
            val order2 = resources[i2].displayOrder
            resources[i1] = resources[i1].copy(displayOrder = order2)
            resources[i2] = resources[i2].copy(displayOrder = order1)
            publish()
        }
    }

    override suspend fun updateResourcesDisplayOrder(resources: List<MediaResource>) {
        resources.forEachIndexed { index, resource ->
            val pos = this.resources.indexOfFirst { it.id == resource.id }
            if (pos >= 0) {
                this.resources[pos] = this.resources[pos].copy(displayOrder = index)
            }
        }
        publish()
    }

    override suspend fun deleteResource(resourceId: Long) {
        deletedResourceIds.add(resourceId)
        resources.removeAll { it.id == resourceId }
        publish()
    }

    override suspend fun deleteAllResources() {
        deleteAllCalled = true
        resources.clear()
        publish()
    }

    override suspend fun testConnection(resource: MediaResource): Result<String> {
        testedConnections.add(resource)
        return testConnectionResult
    }

    override suspend fun updateIcon(resourceId: Long, iconId: String?) {
        val index = resources.indexOfFirst { it.id == resourceId }
        if (index >= 0) {
            resources[index] = resources[index].copy(iconId = iconId)
            publish()
        }
    }

    override suspend fun backfillMissingIcons(
        resolveIcon: (path: String, profileName: String, typeName: String) -> String?,
    ): Int {
        var updated = 0
        resources.forEachIndexed { index, resource ->
            if (resource.iconId == null) {
                val resolved = resolveIcon(resource.path, resource.profile.name, resource.type.name)
                if (resolved != null) {
                    resources[index] = resource.copy(iconId = resolved)
                    updated++
                }
            }
        }
        if (updated > 0) publish()
        return updated
    }
}
