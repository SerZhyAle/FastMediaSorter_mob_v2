package com.sza.fastmediasorter.wear.data.repository

import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WearMediaRepositoryImplTest {

    @Before
    fun setUp() {
        // S3383 made the flat listing query FileDO containers whatever the type switches say, so the
        // store is asked once even with every type off. The android.jar stub answers null for the
        // collection URI, which Kotlin refuses before the query is ever made.
        mockkStatic(MediaStore.Files::class)
        every { MediaStore.Files.getContentUri(any()) } returns mockk()
    }

    @After
    fun tearDown() {
        unmockkStatic(MediaStore.Files::class)
    }

    @Test
    fun `getAllMediaFiles returns empty list when all types are disabled and contentResolver is empty`() = runTest {
        val mockContentResolver = mockk<android.content.ContentResolver> {
            every { query(any(), any(), any(), any(), any()) } returns null
        }
        val mockPrefs = mockk<WearPreferencesRepository> {
            every { isAudioEnabled } returns flowOf(false)
            every { isVideoEnabled } returns flowOf(false)
            every { isImagesEnabled } returns flowOf(false)
            every { isDocumentsEnabled } returns flowOf(false)
        }

        val repository = WearMediaRepositoryImpl(mockContentResolver, mockPrefs)
        val result = repository.getAllMediaFiles()

        result.collect { res ->
            assertTrue(res.exceptionOrNull()?.toString(), res.isSuccess)
            assertEquals(0, res.getOrThrow().size)
        }
    }
}
