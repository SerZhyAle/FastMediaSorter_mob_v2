package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.local.preferences.BrowseStateDataStore
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.ui.browse.BrowseState
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S3069: buildNetworkResourceKey used java.net.URI, which rejects an unencoded space - a legal
 * character in an SMB share name - and threw URISyntaxException from the onCleared path, crashing
 * published builds. The same change fixed a silent divergence: SFTP and FTP keys carried port 445,
 * so they never matched the keys ConnectionThrottleManager had registered the operations under.
 */
class BrowseShutdownCoordinatorKeyTest {

    private val state = MutableStateFlow(BrowseState())

    private fun coordinator(): BrowseShutdownCoordinator = BrowseShutdownCoordinator(
        stateFlow = state,
        ioDispatcher = Dispatchers.Unconfined,
        browseStateDataStore = mockk<BrowseStateDataStore>(relaxed = true),
        unifiedCache = mockk<UnifiedFileCache>(relaxed = true),
        hasActiveTransfer = { false },
        cleanupTrash = {},
        resourceId = 1L,
    )

    @Test
    fun `smb path with a space yields the default-port key instead of throwing`() {
        state.value = BrowseState(
            resource = createMediaResource(path = "smb://192.168.1.10/My Share/Photos 2024", type = ResourceType.SMB)
        )

        assertEquals("smb://192.168.1.10:445", coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `explicit port in an smb path is preserved`() {
        state.value = BrowseState(
            resource = createMediaResource(path = "smb://192.168.1.10:1445/share", type = ResourceType.SMB)
        )

        assertEquals("smb://192.168.1.10:1445", coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `sftp default port is 22, not the smb one`() {
        state.value = BrowseState(
            resource = createMediaResource(path = "sftp://host.example/home/user", type = ResourceType.SFTP)
        )

        assertEquals("sftp://host.example:22", coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `ftp default port is 21, not the smb one`() {
        state.value = BrowseState(
            resource = createMediaResource(path = "ftp://host.example/pub", type = ResourceType.FTP)
        )

        assertEquals("ftp://host.example:21", coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `cloud resource keeps its provider and folder form`() {
        state.value = BrowseState(
            resource = createMediaResource(
                path = "cloud://GOOGLE_DRIVE/folderA",
                type = ResourceType.CLOUD,
                cloudProvider = CloudProvider.GOOGLE_DRIVE,
                cloudFolderId = "folderA",
            )
        )

        assertEquals("cloud://GOOGLE_DRIVE/folderA", coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `local resource has no network key`() {
        state.value = BrowseState(resource = createMediaResource(type = ResourceType.LOCAL))

        assertNull(coordinator().buildNetworkResourceKey())
    }

    @Test
    fun `no resource has no network key`() {
        state.value = BrowseState()

        assertNull(coordinator().buildNetworkResourceKey())
    }
}
