package com.sza.fastmediasorter.ui.main

import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.util.LimitedStorageReach
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2375: pins the visibility of the "Reconnect resource" menu row.
 * Offered only to direct-path local resources whose reach is actually limited by the lack of
 * all-files access (promising documents or allFiles). Media-only folders lose nothing to the
 * narrowing and must not show the row.
 */
class ResourceAdapterReconnectTest {

    private fun createAdapter(
        isDirectPathReconnectCandidate: Boolean = true,
        onReconnectClick: ((MediaResource) -> Unit)? = { _ -> },
    ): ResourceAdapter = ResourceAdapter(
        onItemClick = {},
        onIconClick = {},
        onItemLongClick = {},
        onEditClick = {},
        onCopyFromClick = {},
        onDeleteClick = {},
        onMoveUpClick = {},
        onMoveDownClick = {},
        onMoveToTopClick = {},
        onMoveToBottomClick = {},
        isDirectPathReconnectCandidate = isDirectPathReconnectCandidate,
        onReconnectClick = onReconnectClick,
    )

    @Test
    fun `direct local folder with allFiles when reach is limited is reconnect candidate`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/Download",
            allFiles = true,
        )
        val isReachLimited = LimitedStorageReach.isReachLimited(
            type = resource.type,
            path = resource.path,
            promisedTypes = LimitedStorageReach.promisedTypes(resource),
            allFilesAccessMissing = true,
        )
        assertTrue(isReachLimited)
        assertTrue(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = isReachLimited,
            )
        )
    }

    @Test
    fun `direct local folder with document types when reach is limited is reconnect candidate`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/Download",
            allFiles = false,
            supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.PDF),
        )
        val isReachLimited = LimitedStorageReach.isReachLimited(
            type = resource.type,
            path = resource.path,
            promisedTypes = LimitedStorageReach.promisedTypes(resource),
            allFilesAccessMissing = true,
        )
        assertTrue(isReachLimited)
        assertTrue(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = isReachLimited,
            )
        )
    }

    @Test
    fun `direct local folder with media only types is not reconnect candidate`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/DCIM",
            allFiles = false,
            supportedMediaTypes = setOf(MediaType.IMAGE, MediaType.VIDEO),
        )
        val isReachLimited = LimitedStorageReach.isReachLimited(
            type = resource.type,
            path = resource.path,
            promisedTypes = LimitedStorageReach.promisedTypes(resource),
            allFilesAccessMissing = true,
        )
        assertFalse(isReachLimited)
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = isReachLimited,
            )
        )
    }

    @Test
    fun `direct local folder when all-files access is held is not limited and not reconnect candidate`() {
        val adapter = createAdapter(isDirectPathReconnectCandidate = false)
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/Download",
            allFiles = true,
        )
        val isReachLimited = LimitedStorageReach.isReachLimited(
            type = resource.type,
            path = resource.path,
            promisedTypes = LimitedStorageReach.promisedTypes(resource),
            allFilesAccessMissing = false,
        )
        assertFalse(isReachLimited)
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = false,
                hasReconnectCallback = true,
                isReachLimited = isReachLimited,
            )
        )
    }

    @Test
    fun `null reconnect callback hides reconnect row`() {
        val adapter = createAdapter(onReconnectClick = null)
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "/storage/emulated/0/Download",
            allFiles = true,
        )
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = false,
                isReachLimited = true,
            )
        )
    }

    @Test
    fun `saf tree resource does not offer reconnect`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            allFiles = true,
        )
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = false,
            )
        )
    }

    @Test
    fun `virtual aggregate does not offer reconnect`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.LOCAL,
            path = "virtual://all_docs",
            allFiles = true,
        )
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = true,
            )
        )
    }

    @Test
    fun `network resource does not offer reconnect`() {
        val adapter = createAdapter()
        val resource = createMediaResource(
            type = ResourceType.SMB,
            path = "smb://192.168.1.1/share",
            allFiles = true,
        )
        assertFalse(
            adapter.isReconnectCandidate(
                resource = resource,
                isDirectPathReconnectCandidate = true,
                hasReconnectCallback = true,
                isReachLimited = false,
            )
        )
    }
}
