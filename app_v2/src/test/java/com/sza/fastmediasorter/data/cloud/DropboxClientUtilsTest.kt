package com.sza.fastmediasorter.data.cloud

import com.dropbox.core.DbxException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for [DropboxClientUtils]: error-message classification, credential JSON round-trip,
 * Dropbox Metadata → [CloudFile] mapping, MIME guessing, and the retry wrapper's retriable vs
 * fatal branching. Robolectric supplies org.json/Build; Dropbox SDK metadata is mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class DropboxClientUtilsTest {

    @Test
    fun `buildUserFriendlyErrorMessage flags TLS validation`() {
        val msg = DropboxClientUtils.buildUserFriendlyErrorMessage(
            RuntimeException("Trust anchor for certification path not found")
        )
        assertTrue(msg.contains("TLS/SSL certificate validation failed"))
    }

    @Test
    fun `buildUserFriendlyErrorMessage flags timeout`() {
        val msg = DropboxClientUtils.buildUserFriendlyErrorMessage(RuntimeException("Read timeout occurred"))
        assertTrue(msg.contains("Network timeout"))
    }

    @Test
    fun `buildUserFriendlyErrorMessage flags generic network`() {
        val msg = DropboxClientUtils.buildUserFriendlyErrorMessage(RuntimeException("connection reset"))
        assertTrue(msg.contains("Network connection error"))
    }

    @Test
    fun `buildUserFriendlyErrorMessage falls back to raw message`() {
        val msg = DropboxClientUtils.buildUserFriendlyErrorMessage(RuntimeException("boom"))
        assertEquals("Dropbox authentication failed: boom", msg)
    }

    @Test
    fun `buildUserFriendlyErrorMessage walks cause chain`() {
        val cause = RuntimeException("inner timeout")
        val msg = DropboxClientUtils.buildUserFriendlyErrorMessage(RuntimeException("outer", cause))
        assertTrue(msg.contains("Network timeout"))
    }

    @Test
    fun `serializeAccessToken embeds token and legacy type`() {
        val json = DropboxClientUtils.serializeAccessToken("tok-123")
        assertTrue(json.contains("tok-123"))
        assertTrue(json.contains("legacy"))
    }

    @Test
    fun `serialize then deserialize credential round-trips`() {
        val credential = DbxCredential("access-x", 9_999_999_999L, "refresh-y", "app-key-z")
        val json = DropboxClientUtils.serializeCredential(credential)

        val restored = DropboxClientUtils.deserializeCredential(json, fallbackAppKey = "fallback")
        assertNotNull(restored)
        assertEquals("access-x", restored!!.accessToken)
        assertEquals("refresh-y", restored.refreshToken)
        assertEquals("app-key-z", restored.appKey)
        assertEquals(9_999_999_999L, restored.expiresAt)
    }

    @Test
    fun `deserializeCredential uses fallback app key when absent`() {
        val json = org.json.JSONObject().put("access_token", "a").toString()
        val restored = DropboxClientUtils.deserializeCredential(json, fallbackAppKey = "fb-key")
        assertNotNull(restored)
        assertEquals("fb-key", restored!!.appKey)
        assertNull(restored.refreshToken)
    }

    @Test
    fun `deserializeCredential returns null on malformed json`() {
        assertNull(DropboxClientUtils.deserializeCredential("not json", "fb"))
    }

    @Test
    fun `metadataToCloudFile maps file metadata`() {
        val md = mockk<FileMetadata>()
        every { md.pathDisplay } returns "/dir/photo.jpg"
        every { md.pathLower } returns "/dir/photo.jpg"
        every { md.name } returns "photo.jpg"
        every { md.size } returns 4096L
        every { md.serverModified } returns Date(1_700_000_000_000L)

        val file = DropboxClientUtils.metadataToCloudFile(md, "/parent")

        assertEquals("/dir/photo.jpg", file.id)
        assertEquals("photo.jpg", file.name)
        assertEquals("/parent", file.path)
        assertFalse(file.isFolder)
        assertEquals(4096L, file.size)
        assertEquals(1_700_000_000_000L, file.modifiedDate)
        assertEquals("image/jpg", file.mimeType)
    }

    @Test
    fun `metadataToCloudFile maps folder metadata`() {
        val md = mockk<FolderMetadata>()
        every { md.pathDisplay } returns "/dir"
        every { md.pathLower } returns "/dir"
        every { md.name } returns "dir"

        val file = DropboxClientUtils.metadataToCloudFile(md, "/parent")

        assertTrue(file.isFolder)
        assertEquals(0L, file.size)
        assertNull(file.mimeType)
    }

    @Test
    fun `guessMimeType classifies by extension`() {
        assertEquals("image/jpg", DropboxClientUtils.guessMimeType("a.jpg"))
        assertEquals("video/mp4", DropboxClientUtils.guessMimeType("a.mp4"))
        assertEquals("audio/mp3", DropboxClientUtils.guessMimeType("a.mp3"))
        assertNull(DropboxClientUtils.guessMimeType("a.bin"))
        assertNull(DropboxClientUtils.guessMimeType("noext"))
    }

    @Test
    fun `withRetry returns immediately on success`() = runTest {
        var calls = 0
        val result = DropboxClientUtils.withRetry("op", maxAttempts = 3, retryDelayMs = 0L) {
            calls++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `withRetry retries retriable DbxException then succeeds`() = runTest {
        var calls = 0
        val result = DropboxClientUtils.withRetry("op", maxAttempts = 3, retryDelayMs = 0L) {
            calls++
            if (calls == 1) throw DbxException("401 unauthorized")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, calls)
    }

    @Test
    fun `withRetry rethrows non-retriable DbxException immediately`() {
        var calls = 0
        val ex = runCatching {
            runBlocking {
                DropboxClientUtils.withRetry("op", maxAttempts = 3, retryDelayMs = 0L) {
                    calls++
                    throw DbxException("400 bad request")
                }
            }
        }.exceptionOrNull()
        assertTrue(ex is DbxException)
        assertEquals(1, calls)
    }

    @Test
    fun `withRetry retries network exception then rethrows after exhaustion`() {
        var calls = 0
        val ex = runCatching {
            runBlocking {
                DropboxClientUtils.withRetry("op", maxAttempts = 2, retryDelayMs = 0L) {
                    calls++
                    throw RuntimeException("network down")
                }
            }
        }.exceptionOrNull()
        assertNotNull(ex)
        assertEquals(2, calls)
    }

    @Test
    fun `withRetry rethrows non-network plain exception immediately`() {
        var calls = 0
        val ex = runCatching {
            runBlocking {
                DropboxClientUtils.withRetry("op", maxAttempts = 3, retryDelayMs = 0L) {
                    calls++
                    throw IllegalStateException("logic error")
                }
            }
        }.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertEquals(1, calls)
    }
}
