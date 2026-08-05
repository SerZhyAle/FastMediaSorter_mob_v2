package com.sza.fastmediasorter.ui.icon

import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S1289: the icon key must track exactly what [ResourceIconComposer] branches on - no more, or the
 * launcher desktop rebuilds on changes it cannot see; no less, or a changed icon never reaches it.
 */
class ResourceIconKeyTest {

    private fun resource(
        path: String = "/storage/emulated/0/Pictures",
        type: ResourceType = ResourceType.LOCAL,
        iconId: String? = null,
        storageVolumeId: String? = null,
        cloudProvider: CloudProvider? = null,
        fileCount: Int = 0,
        lastAccessedDate: Long = 0L,
    ) = MediaResource(
        id = 1L,
        name = "Pictures",
        path = path,
        type = type,
        iconId = iconId,
        storageVolumeId = storageVolumeId,
        cloudProvider = cloudProvider,
        fileCount = fileCount,
        lastAccessedDate = lastAccessedDate,
    )

    @Test
    fun `identical resources share a key`() {
        assertEquals(resourceIconKey(resource()), resourceIconKey(resource()))
    }

    @Test
    fun `a user-assigned icon changes the key`() {
        assertNotEquals(
            resourceIconKey(resource(iconId = null)),
            resourceIconKey(resource(iconId = "ico-01-007")),
        )
    }

    @Test
    fun `a removable volume changes the key`() {
        assertNotEquals(
            resourceIconKey(resource(storageVolumeId = null)),
            resourceIconKey(resource(storageVolumeId = "volume-a1b2")),
        )
    }

    @Test
    fun `a cloud provider changes the key`() {
        assertNotEquals(
            resourceIconKey(resource(type = ResourceType.CLOUD, cloudProvider = null)),
            resourceIconKey(resource(type = ResourceType.CLOUD, cloudProvider = CloudProvider.GOOGLE_DRIVE)),
        )
    }

    @Test
    fun `the path decides the virtual-folder glyph and so changes the key`() {
        assertNotEquals(
            resourceIconKey(resource(path = "/storage/emulated/0/Pictures")),
            resourceIconKey(resource(path = "/storage/emulated/0/Music")),
        )
    }

    @Test
    fun `fields the composer ignores leave the key alone`() {
        assertEquals(
            resourceIconKey(resource(fileCount = 0, lastAccessedDate = 0L)),
            resourceIconKey(resource(fileCount = 4096, lastAccessedDate = 1_700_000_000_000L)),
        )
    }
}
