package com.sza.fastmediasorter.wear.data.repository

import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.every
import io.mockk.mockk

class WearMediaRepositoryImplTest {

    @Test
    fun `getAllMediaFiles returns empty list when all types are disabled and contentResolver is empty`() = runTest {
        val mockContentResolver = mockk<android.content.ContentResolver>()
        val mockPrefs = mockk<WearPreferencesRepository> {
            every { isAudioEnabled } returns flowOf(false)
            every { isVideoEnabled } returns flowOf(false)
            every { isImagesEnabled } returns flowOf(false)
            every { isDocumentsEnabled } returns flowOf(false)
        }

        val repository = WearMediaRepositoryImpl(mockContentResolver, mockPrefs)
        val result = repository.getAllMediaFiles()

        result.collect { res ->
            assertTrue(res.isSuccess)
            assertEquals(0, res.getOrThrow().size)
        }
    }
}
