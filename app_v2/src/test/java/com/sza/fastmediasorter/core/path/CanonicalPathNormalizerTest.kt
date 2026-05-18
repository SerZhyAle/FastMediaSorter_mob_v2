package com.sza.fastmediasorter.core.path

import android.net.Uri
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CanonicalPathNormalizer].
 *
 * `android.net.Uri` is stubbed via MockK because robolectric is not on this module's
 * test classpath - the normalizer only uses `Uri.parse(..).path` and `Uri.decode(..)`.
 */
class CanonicalPathNormalizerTest {

    private lateinit var normalizer: CanonicalPathNormalizer

    @Before
    fun setUp() {
        normalizer = CanonicalPathNormalizer()
        mockkStatic(Uri::class)
        // Default Uri.decode = identity (test inputs are already decoded except where noted).
        every { Uri.decode(any<String>()) } answers { firstArg() }
        // %20 -> space (the one case the SMB test exercises).
        every { Uri.decode("Folder%20A") } returns "Folder A"
        // content:// stubs used by LOCAL branch.
        every { Uri.parse("content://media/external/images/12345") } returns mockContentUri("/external/images/12345")
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `LOCAL identity for already-canonical absolute path`() {
        val out = normalizer.canonical("/sdcard/DCIM/IMG.jpg", ResourceType.LOCAL)
        assertEquals("/sdcard/DCIM/IMG.jpg", out)
    }

    @Test
    fun `LOCAL content URI reduces to path component`() {
        val out = normalizer.canonical("content://media/external/images/12345", ResourceType.LOCAL)
        assertEquals("/external/images/12345", out)
    }

    @Test
    fun `LOCAL dot-dot segment is folded by File canonicalPath`() {
        val a = normalizer.canonical("/sdcard/DCIM/../DCIM/IMG.jpg", ResourceType.LOCAL)
        val b = normalizer.canonical("/sdcard/DCIM/IMG.jpg", ResourceType.LOCAL)
        assertEquals(b, a)
    }

    @Test
    fun `SMB normalizes host case and URL-decoded segments`() {
        val a = normalizer.canonical("smb://Server/Share/Folder%20A/file.jpg", ResourceType.SMB)
        val b = normalizer.canonical("smb://server/Share/Folder A/file.jpg", ResourceType.SMB)
        assertEquals(b, a)
    }

    @Test
    fun `DROPBOX lowercases path and strips trailing slash`() {
        val a = normalizer.canonical("/Photos/IMG.JPG", ResourceType.CLOUD)
        val b = normalizer.canonical("/photos/img.jpg", ResourceType.CLOUD)
        assertEquals(b, a)
    }

    @Test
    fun `GOOGLE_DRIVE strips gdrive scheme to bare fileId`() {
        val a = normalizer.canonical("gdrive:/abc123", ResourceType.CLOUD)
        val b = normalizer.canonical("abc123", ResourceType.CLOUD)
        assertEquals(b, a)
    }

    @Test
    fun `idempotent for every covered case`() {
        val cases: List<Pair<String, ResourceType>> = listOf(
            "/sdcard/DCIM/IMG.jpg" to ResourceType.LOCAL,
            "content://media/external/images/12345" to ResourceType.LOCAL,
            "/sdcard/DCIM/../DCIM/IMG.jpg" to ResourceType.LOCAL,
            "smb://Server/Share/Folder%20A/file.jpg" to ResourceType.SMB,
            "/Photos/IMG.JPG" to ResourceType.CLOUD,
            "gdrive:/abc123" to ResourceType.CLOUD
        )
        for ((raw, type) in cases) {
            val once = normalizer.canonical(raw, type)
            val twice = normalizer.canonical(once, type)
            assertEquals("idempotent for $raw / $type", once, twice)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun mockContentUri(pathPart: String): Uri {
        val uri = io.mockk.mockk<Uri>()
        every { uri.path } returns pathPart
        return uri
    }
}
