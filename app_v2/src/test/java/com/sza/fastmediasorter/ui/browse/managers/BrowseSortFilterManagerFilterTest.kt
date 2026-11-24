package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.browse.BrowseState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4699: a restored or changed filter must never start a scan of its own while the resource
 * load is the authoritative one, and must re-filter the unfiltered cached list once it exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; the LruCache inside the cache needs the framework.
class BrowseSortFilterManagerFilterTest {

    private val resourceId = 4699L
    private val state = MutableStateFlow(BrowseState(resource = resource()))
    private var loading = false
    private var loadCalls = 0
    private val managerScope = CoroutineScope(Dispatchers.Unconfined)

    private val manager = BrowseSortFilterManager(
        updateResourceUseCase = mockk(relaxed = true),
        getResourcesUseCase = mockk(relaxed = true),
        scope = managerScope,
        ioDispatcher = Dispatchers.Unconfined,
        stateFlow = state,
        updateState = { update -> state.value = update(state.value) },
        setLoading = { loading = it },
        isLoading = { loading },
        loadMediaFiles = { loadCalls++ },
        fileListManager = mockk(relaxed = true),
        resourceId = resourceId
    )

    @After
    fun clear() {
        runBlocking { managerScope.coroutineContext.job.cancelAndJoin() }
        MediaFilesCacheManager.clearAllCaches()
    }

    @Test
    fun `filter restored while the load runs starts no second scan`() {
        loading = true

        manager.setFilter(FileFilter(nameContains = "a"))

        assertEquals(0, loadCalls)
    }

    @Test
    fun `filter restored before the resource is known does nothing`() {
        state.value = BrowseState()

        manager.setFilter(FileFilter(nameContains = "a"))

        assertEquals(0, loadCalls)
        assertEquals(FileFilter(nameContains = "a"), state.value.filter)
    }

    @Test
    fun `filter after a failed load reloads through the authoritative path once`() {
        manager.setFilter(FileFilter(nameContains = "a"))

        assertEquals(1, loadCalls)
    }

    @Test
    fun `filter after the load re-filters the cached list without scanning`() {
        MediaFilesCacheManager.setCachedList(resourceId, listOf(file("alpha"), file("beta")))

        manager.setFilter(FileFilter(nameContains = "alp"))

        assertEquals(listOf("alpha"), state.value.mediaFiles.map { it.name })
        assertEquals(0, loadCalls)
    }

    @Test
    fun `clearing the filter restores every cached file`() {
        MediaFilesCacheManager.setCachedList(resourceId, listOf(file("alpha"), file("beta")))
        manager.setFilter(FileFilter(nameContains = "alp"))

        manager.setFilter(null)

        assertEquals(listOf("alpha", "beta"), state.value.mediaFiles.map { it.name })
    }

    @Test
    fun `an empty finished load re-filters nothing instead of rescanning`() {
        MediaFilesCacheManager.setCachedList(resourceId, emptyList())

        manager.setFilter(FileFilter(nameContains = "a"))

        assertEquals(emptyList<MediaFile>(), state.value.mediaFiles)
        assertEquals(0, loadCalls)
    }

    @Test
    fun `published batches carry the filter current at publish time`() {
        val batch = listOf(file("alpha"), file("beta"))
        assertEquals(batch, manager.filterForDisplay(batch))

        state.value = state.value.copy(filter = FileFilter(nameContains = "bet"))

        assertEquals(listOf("beta"), manager.filterForDisplay(batch).map { it.name })
    }

    private fun resource(): MediaResource = mockk(relaxed = true) {
        every { id } returns resourceId
    }

    private fun file(name: String) = MediaFile(
        name = name,
        path = "/storage/$name",
        type = MediaType.IMAGE,
        size = 1L,
        createdDate = 0L
    )
}
