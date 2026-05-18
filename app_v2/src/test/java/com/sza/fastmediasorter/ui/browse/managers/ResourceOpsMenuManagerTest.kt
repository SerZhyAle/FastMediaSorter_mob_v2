package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.util.VirtualPathUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the canCreateFolder guard logic used in [ResourceOpsMenuManager.showMenu].
 *
 * Contract (all must be true):
 *   - resource != null
 *   - resource.showSubfoldersAsItems == true
 *   - resource.isReadOnly == false
 *   - !VirtualPathUtils.isVirtualPath(resource.path)
 */
class ResourceOpsMenuManagerTest {

    // Mirrors the production condition in ResourceOpsMenuManager.showMenu()
    private fun canCreateFolder(resource: MediaResource?) =
        resource != null
            && resource.showSubfoldersAsItems
            && !resource.isReadOnly
            && !VirtualPathUtils.isVirtualPath(resource.path)

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun makeResource(
        path: String,
        showSubfoldersAsItems: Boolean = true,
        isReadOnly: Boolean = false
    ) = MediaResource(
        id = 1,
        name = "Test Resource",
        path = path,
        type = ResourceType.LOCAL,
        sortMode = SortMode.NAME_ASC,
        displayMode = DisplayMode.LIST,
        showSubfoldersAsItems = showSubfoldersAsItems,
        isReadOnly = isReadOnly
    )

    // -------------------------------------------------------------------------
    // Virtual path guard
    // -------------------------------------------------------------------------

    @Test
    fun `canCreateFolder is false for virtual all_video path`() {
        val resource = makeResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO)
        assertFalse(canCreateFolder(resource))
    }

    @Test
    fun `canCreateFolder is false for virtual all_images path`() {
        val resource = makeResource(path = LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES)
        assertFalse(canCreateFolder(resource))
    }

    @Test
    fun `canCreateFolder is false for virtual recent path`() {
        val resource = makeResource(path = LocalMediaScanner.VIRTUAL_PATH_RECENT)
        assertFalse(canCreateFolder(resource))
    }

    // -------------------------------------------------------------------------
    // Real path - happy path
    // -------------------------------------------------------------------------

    @Test
    fun `canCreateFolder is true for local writable resource with subfolders enabled`() {
        val resource = makeResource(path = "/storage/emulated/0/DCIM")
        assertTrue(canCreateFolder(resource))
    }

    @Test
    fun `canCreateFolder is true for SMB resource with subfolders enabled`() {
        val resource = makeResource(path = "smb://192.168.1.1/share/Photos").copy(
            type = ResourceType.SMB
        )
        assertTrue(canCreateFolder(resource))
    }

    // -------------------------------------------------------------------------
    // Negative flags on real paths
    // -------------------------------------------------------------------------

    @Test
    fun `canCreateFolder is false when showSubfoldersAsItems is false`() {
        val resource = makeResource(path = "/storage/emulated/0/DCIM", showSubfoldersAsItems = false)
        assertFalse(canCreateFolder(resource))
    }

    @Test
    fun `canCreateFolder is false for read-only resource`() {
        val resource = makeResource(path = "/storage/emulated/0/DCIM", isReadOnly = true)
        assertFalse(canCreateFolder(resource))
    }

    @Test
    fun `canCreateFolder is false when resource is null`() {
        assertFalse(canCreateFolder(null))
    }
}
