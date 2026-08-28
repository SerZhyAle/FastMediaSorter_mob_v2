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
 * Unit tests for [OneDriveRestClientUtils]: MSAL account JSON round-trip, cloud-item-reference
 * prefix stripping, and Graph DriveItem JSON → [CloudFile] mapping (folder marker, ISO-8601 time,
 * nested thumbnail extraction). Robolectric supplies real org.json.
 *
 * S0403: this test stays in the shared `src/test` set because its subject stays in `src/main` -
 * `serializeAccount` now takes the three account fields instead of the MSAL `IAccount`, so nothing
 * here names a type that a FOSS build does not link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class OneDriveRestClientUtilsTest {

    @Test
    fun `serializeAccount then deserializeAccount round-trips username`() {
        val json = OneDriveRestClientUtils.serializeAccount(
            username = "user@example.com",
            id = "id-123",
            authority = "https://login"
        )
        assertEquals("user@example.com", OneDriveRestClientUtils.deserializeAccount(json))
    }

    @Test
    fun `deserializeAccount returns empty for malformed json`() {
        assertEquals("", OneDriveRestClientUtils.deserializeAccount("not json"))
    }

    @Test
    fun `deserializeAccount returns empty when username absent`() {
        assertEquals("", OneDriveRestClientUtils.deserializeAccount(JSONObject().put("id", "x").toString()))
    }

    @Test
    fun `normalizeCloudItemReference strips onedrive scheme prefix`() {
        assertEquals("01ABCDE", OneDriveRestClientUtils.normalizeCloudItemReference("cloud://onedrive/01ABCDE"))
    }

    @Test
    fun `normalizeCloudItemReference leaves bare id untouched`() {
        assertEquals("01ABCDE", OneDriveRestClientUtils.normalizeCloudItemReference("01ABCDE"))
    }

    @Test
    fun `parseItem maps fields and detects folder marker`() {
        val json = JSONObject()
            .put("id", "ITEM1")
            .put("name", "doc.txt")
            .put("size", 512L)
            .put("lastModifiedDateTime", "2024-11-17T12:00:00Z")
            .put("mimeType", "text/plain")
            .put("webUrl", "https://web")

        val file = OneDriveRestClientUtils.parseItem(json, "/p")

        assertEquals("ITEM1", file.id)
        assertEquals("doc.txt", file.name)
        assertEquals("/p", file.path)
        assertFalse(file.isFolder)
        assertEquals(512L, file.size)
        assertEquals(java.time.Instant.parse("2024-11-17T12:00:00Z").toEpochMilli(), file.modifiedDate)
        assertEquals("text/plain", file.mimeType)
        assertEquals("https://web", file.webViewUrl)
        assertNull(file.thumbnailUrl)
    }

    @Test
    fun `parseItem detects folder via folder facet`() {
        val json = JSONObject()
            .put("id", "DIR")
            .put("name", "Folder")
            .put("folder", JSONObject().put("childCount", 3))

        assertTrue(OneDriveRestClientUtils.parseItem(json, "/p").isFolder)
    }

    @Test
    fun `parseItem extracts nested thumbnail large url`() {
        val thumbnails = JSONArray().put(
            JSONObject().put("large", JSONObject().put("url", "https://large"))
        )
        val json = JSONObject()
            .put("id", "I")
            .put("name", "n")
            .put("thumbnails", thumbnails)

        assertEquals("https://large", OneDriveRestClientUtils.parseItem(json, "/p").thumbnailUrl)
    }

    @Test
    fun `parseItem falls back to zero time on malformed value`() {
        val json = JSONObject()
            .put("id", "I")
            .put("name", "n")
            .put("lastModifiedDateTime", "bad")

        assertEquals(0L, OneDriveRestClientUtils.parseItem(json, "/p").modifiedDate)
    }

    @Test
    fun `parseItems maps all elements`() {
        val arr = JSONArray()
            .put(JSONObject().put("id", "A").put("name", "a"))
            .put(JSONObject().put("id", "B").put("name", "b"))

        val files = OneDriveRestClientUtils.parseItems(arr, "/root")
        assertEquals(2, files.size)
        assertEquals("B", files[1].id)
    }
}
