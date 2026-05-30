package com.sza.fastmediasorter.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [GoogleDriveRestClientUtils] Drive JSON → [CloudFile] mapping: folder detection,
 * RFC-3339 modifiedTime parsing (incl. malformed fallback), optional thumbnail/webView extraction,
 * and parent-path composition. Robolectric supplies the real org.json implementation (S0223 pattern).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class GoogleDriveRestClientUtilsTest {

    @Test
    fun `parseItem maps file fields and parses RFC3339 time`() {
        val json = JSONObject()
            .put("id", "FILE1")
            .put("name", "photo.jpg")
            .put("mimeType", "image/jpeg")
            .put("size", 2048L)
            .put("modifiedTime", "2024-11-17T12:00:00.000Z")
            .put("thumbnailLink", "https://thumb")
            .put("webViewLink", "https://view")

        val file = GoogleDriveRestClientUtils.parseItem(json, "/root")

        assertEquals("FILE1", file.id)
        assertEquals("photo.jpg", file.name)
        assertEquals("/root/photo.jpg", file.path)
        assertFalse(file.isFolder)
        assertEquals(2048L, file.size)
        assertEquals(java.time.Instant.parse("2024-11-17T12:00:00.000Z").toEpochMilli(), file.modifiedDate)
        assertEquals("image/jpeg", file.mimeType)
        assertEquals("https://thumb", file.thumbnailUrl)
        assertEquals("https://view", file.webViewUrl)
    }

    @Test
    fun `parseItem detects folder mimeType`() {
        val json = JSONObject()
            .put("id", "DIR1")
            .put("name", "Album")
            .put("mimeType", "application/vnd.google-apps.folder")

        val file = GoogleDriveRestClientUtils.parseItem(json, "/root")

        assertTrue(file.isFolder)
    }

    @Test
    fun `parseItem falls back to zero on malformed modifiedTime`() {
        val json = JSONObject()
            .put("id", "F")
            .put("name", "n")
            .put("modifiedTime", "not-a-date")

        assertEquals(0L, GoogleDriveRestClientUtils.parseItem(json, "/p").modifiedDate)
    }

    @Test
    fun `parseItem uses zero time and null optionals when absent`() {
        val json = JSONObject().put("id", "F").put("name", "n")

        val file = GoogleDriveRestClientUtils.parseItem(json, "/p")

        assertEquals(0L, file.modifiedDate)
        assertNull(file.mimeType)
        assertNull(file.thumbnailUrl)
        assertNull(file.webViewUrl)
        assertEquals(0L, file.size)
    }

    @Test
    fun `parseItems maps every element`() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "A").put("name", "a"))
            .put(JSONObject().put("id", "B").put("name", "b"))

        val files = GoogleDriveRestClientUtils.parseItems(arr, "/root")

        assertEquals(2, files.size)
        assertEquals("A", files[0].id)
        assertEquals("/root/b", files[1].path)
    }

    @Test
    fun `parseItems empty array yields empty list`() {
        assertTrue(GoogleDriveRestClientUtils.parseItems(JSONArray(), "/root").isEmpty())
    }
}
