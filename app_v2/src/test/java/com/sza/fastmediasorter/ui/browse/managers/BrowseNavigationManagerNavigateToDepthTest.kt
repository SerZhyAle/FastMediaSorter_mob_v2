package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.ui.browse.BrowseState
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * S0917: breadcrumb clicks at an arbitrary depth must navigate for cloud resources too.
 *
 * [BrowseNavigationManager.navigateToDepth] used to string-parse currentPath against
 * resource.path; cloud resources use flat opaque-id paths ("cloud://provider/<id>") that never
 * matched, so any non-adjacent cloud breadcrumb silently no-op'd. The fix reconstructs the
 * target from the tracked pathStack/folderNameStack by breadcrumb index (path-agnostic).
 *
 * The launched loadDirectoryContents coroutine is left pending on the un-advanced
 * StandardTestDispatcher; navigateToDepth applies its state mutation synchronously, so the
 * assertions below read the deterministic post-navigation state without running any I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowseNavigationManagerNavigateToDepthTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val state = MutableStateFlow(BrowseState())

    private fun manager(): BrowseNavigationManager = BrowseNavigationManager(
        context = mockk<Context>(relaxed = true),
        scope = TestScope(dispatcherRule.testDispatcher),
        ioDispatcher = dispatcherRule.testDispatcher,
        stateFlow = state,
        updateState = { transform -> state.value = transform(state.value) },
        sendEvent = {},
        setLoading = {},
        mediaScannerFactory = mockk<MediaScannerFactory>(relaxed = true),
        cancelLoad = {},
        onLoadMediaFiles = {},
        onLoadResource = {},
    )

    /** State three levels deep in a cloud resource with flat opaque-id paths. */
    private fun cloudThreeLevelsDeep() {
        state.value = BrowseState(
            resource = createMediaResource(path = CLOUD_ROOT),
            isSubfolderMode = true,
            currentPath = "cloud://gdrive/cId",
            pathStack = listOf(CLOUD_ROOT, "cloud://gdrive/aId", "cloud://gdrive/bId"),
            currentFolderName = "Summer",
            folderNameStack = listOf("Photos", "2024"),
        )
    }

    @Test
    fun cloudResource_clickIntermediateDepth_navigates() {
        cloudThreeLevelsDeep()

        manager().navigateToDepth(1)

        val s = state.value
        assertEquals("cloud://gdrive/aId", s.currentPath)
        assertEquals(listOf(CLOUD_ROOT), s.pathStack)
        assertEquals(emptyList<String>(), s.folderNameStack)
        assertEquals("Photos", s.currentFolderName)
    }

    @Test
    fun cloudResource_clickRoot_resetsToResourceRoot() {
        cloudThreeLevelsDeep()

        manager().navigateToDepth(0)

        val s = state.value
        assertEquals(CLOUD_ROOT, s.currentPath)
        assertEquals(emptyList<String>(), s.pathStack)
        assertEquals(emptyList<String>(), s.folderNameStack)
        assertNull(s.currentFolderName)
    }

    @Test
    fun cloudResource_clickDeeperIntermediateDepth_navigates() {
        cloudThreeLevelsDeep()

        manager().navigateToDepth(2)

        val s = state.value
        assertEquals("cloud://gdrive/bId", s.currentPath)
        assertEquals(listOf(CLOUD_ROOT, "cloud://gdrive/aId"), s.pathStack)
        assertEquals(listOf("Photos"), s.folderNameStack)
        assertEquals("2024", s.currentFolderName)
    }

    @Test
    fun localHierarchicalResource_clickIntermediateDepth_stillWorks() {
        val root = "/storage/emulated/0/Test"
        state.value = BrowseState(
            resource = createMediaResource(path = root),
            isSubfolderMode = true,
            currentPath = "$root/A/B",
            pathStack = listOf(root, "$root/A"),
            currentFolderName = "B",
            folderNameStack = listOf("A"),
        )

        manager().navigateToDepth(1)

        val s = state.value
        assertEquals("$root/A", s.currentPath)
        assertEquals(listOf(root), s.pathStack)
        assertEquals("A", s.currentFolderName)
    }

    @Test
    fun depthOutOfRange_isNoOp() {
        cloudThreeLevelsDeep()

        manager().navigateToDepth(5)

        assertEquals("cloud://gdrive/cId", state.value.currentPath)
    }

    @Test
    fun clickingCurrentDepth_isNoOp() {
        cloudThreeLevelsDeep()

        manager().navigateToDepth(3) // last index == current level

        assertEquals("cloud://gdrive/cId", state.value.currentPath)
    }

    private companion object {
        const val CLOUD_ROOT = "cloud://gdrive/rootId"
    }
}
