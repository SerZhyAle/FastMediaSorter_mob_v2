package com.sza.fastmediasorter.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MediaStoreDateUtilsTest {

    @Test
    fun `resolveCreatedDate falls back to file lastModified when MediaStore date is zero`() {
        val tempFile = File.createTempFile("media-store-date", ".jpg")
        val expectedMs = 1_700_000_000_000L
        tempFile.setLastModified(expectedMs)

        try {
            val result = MediaStoreDateUtils.resolveCreatedDate(tempFile.absolutePath, 0L)
            assertEquals(expectedMs, result)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `resolveCreatedDate prefers MediaStore date when it is valid`() {
        val tempFile = File.createTempFile("media-store-date-valid", ".jpg")
        val mediaStoreMs = 1_800_000_000_000L
        val fileMs = 1_700_000_000_000L
        tempFile.setLastModified(fileMs)

        try {
            val result = MediaStoreDateUtils.resolveCreatedDate(tempFile.absolutePath, mediaStoreMs / 1000L)
            assertEquals(mediaStoreMs, result)
        } finally {
            tempFile.delete()
        }
    }
}
