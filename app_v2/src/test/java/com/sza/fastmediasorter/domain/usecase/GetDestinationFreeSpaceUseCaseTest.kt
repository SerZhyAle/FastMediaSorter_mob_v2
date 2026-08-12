package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.StorageVolumeInfo
import com.sza.fastmediasorter.domain.repository.StorageVolumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The regression these tests exist for: a destination on a card measured against built-in storage.
 * Every case therefore gives the two volumes different free-byte counts, so a reading taken from
 * the wrong volume cannot accidentally match the expected one.
 */
class GetDestinationFreeSpaceUseCaseTest {

    private val repository = mockk<StorageVolumeRepository>()
    private lateinit var useCase: GetDestinationFreeSpaceUseCase

    private val primary = StorageVolumeInfo(
        id = StorageVolumeInfo.PRIMARY_VOLUME_ID,
        displayName = "Internal storage",
        isRemovable = false,
        isMounted = true,
        mountPath = PRIMARY_MOUNT,
        totalBytes = PRIMARY_TOTAL,
        availableBytes = PRIMARY_FREE
    )

    private val card = StorageVolumeInfo(
        id = CARD_ID,
        displayName = "SD card",
        isRemovable = true,
        isMounted = true,
        mountPath = CARD_MOUNT,
        totalBytes = CARD_TOTAL,
        availableBytes = CARD_FREE
    )

    @Before
    fun setup() {
        useCase = GetDestinationFreeSpaceUseCase(repository)
        val volumes = listOf(primary, card)
        coEvery { repository.getVolumes() } returns volumes
        coEvery { repository.getVolume(any()) } answers {
            val requested = firstArg<String>()
            volumes.firstOrNull { it.id.equals(requested, ignoreCase = true) }
        }
    }

    @Test
    fun `document tree on a removable volume reports that volume`() = runTest {
        val destination = "content://com.android.externalstorage.documents/tree/$CARD_ID%3ADCIM"
        assertEquals(CARD_FREE, useCase(destination))
    }

    @Test
    fun `document tree on primary reports primary`() = runTest {
        val destination = "content://com.android.externalstorage.documents/tree/primary%3APictures"
        assertEquals(PRIMARY_FREE, useCase(destination))
    }

    @Test
    fun `filesystem path under the primary mount reports primary`() = runTest {
        assertEquals(PRIMARY_FREE, useCase("$PRIMARY_MOUNT/Pictures"))
    }

    @Test
    fun `filesystem path on the card reports the card`() = runTest {
        assertEquals(CARD_FREE, useCase("$CARD_MOUNT/DCIM"))
    }

    @Test
    fun `unattributable destination returns null`() = runTest {
        assertNull(useCase("smb://server/share/folder"))
    }

    @Test
    fun `document tree on an unknown volume returns null`() = runTest {
        assertNull(useCase("content://com.android.externalstorage.documents/tree/AAAA-BBBB%3ADCIM"))
    }

    @Test
    fun `a sibling path sharing the mount prefix is not claimed`() = runTest {
        assertNull(useCase("${PRIMARY_MOUNT}1/Pictures"))
    }

    private companion object {
        const val CARD_ID = "1234-5678"
        const val PRIMARY_MOUNT = "/storage/emulated/0"
        const val CARD_MOUNT = "/storage/1234-5678"
        const val PRIMARY_FREE = 5_000_000_000L
        const val PRIMARY_TOTAL = 60_000_000_000L
        const val CARD_FREE = 900_000_000L
        const val CARD_TOTAL = 32_000_000_000L
    }
}
