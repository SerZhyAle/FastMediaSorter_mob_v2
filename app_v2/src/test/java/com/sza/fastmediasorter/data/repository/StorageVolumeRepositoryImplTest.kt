package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1378 Phase 01 - the resolution rules `UriPathResolver` depends on, driven through a fake
 * [StorageVolumeSource] because the platform enumeration relies on hidden-API reflection that no
 * unit test can exercise.
 */
class StorageVolumeRepositoryImplTest {

    private val primary = StorageVolumeInfo(
        id = StorageVolumeInfo.PRIMARY_VOLUME_ID,
        displayName = "Internal storage",
        isRemovable = false,
        isMounted = true,
        mountPath = "/storage/emulated/0",
        totalBytes = 128_000_000_000L,
        availableBytes = 64_000_000_000L
    )

    private val card = StorageVolumeInfo(
        id = "1A2B-3C4D",
        displayName = "SD card",
        isRemovable = true,
        isMounted = true,
        mountPath = "/storage/1A2B-3C4D",
        totalBytes = 32_000_000_000L,
        availableBytes = 8_000_000_000L
    )

    private val ejectedCard = card.copy(
        id = "9F8E-7D6C",
        displayName = "SD card 2",
        isMounted = false,
        mountPath = null,
        totalBytes = 0L,
        availableBytes = 0L
    )

    private fun repository(vararg volumes: StorageVolumeInfo) =
        StorageVolumeRepositoryImpl(FakeStorageVolumeSource(volumes.toList()))

    @Test
    fun `primary volume id resolves to primary storage`() = runTest {
        val path = repository(primary, card).resolveMountPath(StorageVolumeInfo.PRIMARY_VOLUME_ID)

        assertEquals("/storage/emulated/0", path)
    }

    @Test
    fun `volume uuid resolves to that volume mount path`() = runTest {
        val path = repository(primary, card).resolveMountPath("1A2B-3C4D")

        assertEquals("/storage/1A2B-3C4D", path)
    }

    @Test
    fun `uuid lookup ignores case`() = runTest {
        val path = repository(primary, card).resolveMountPath("1a2b-3c4d")

        assertEquals("/storage/1A2B-3C4D", path)
    }

    @Test
    fun `unknown uuid resolves to null`() = runTest {
        val path = repository(primary, card).resolveMountPath("0000-0000")

        assertNull(path)
    }

    @Test
    fun `unmounted volume is listed rather than dropped`() = runTest {
        val volumes = repository(primary, ejectedCard).getVolumes()

        assertEquals(2, volumes.size)
        val ejected = volumes.single { it.id == "9F8E-7D6C" }
        assertFalse(ejected.isMounted)
        assertEquals(0L, ejected.availableBytes)
    }

    @Test
    fun `unmounted volume resolves to no mount path`() = runTest {
        val path = repository(primary, ejectedCard).resolveMountPath("9F8E-7D6C")

        assertNull(path)
    }

    private class FakeStorageVolumeSource(
        private val volumes: List<StorageVolumeInfo>
    ) : StorageVolumeSource {

        override fun listVolumes(): List<StorageVolumeInfo> = volumes

        override fun mountPathFor(volumeId: String): String? =
            volumes.firstOrNull { it.id.equals(volumeId, ignoreCase = true) }
                ?.takeIf { it.isMounted }
                ?.mountPath
    }
}
