package com.sza.fastmediasorter.core.path

import android.net.Uri
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CanonicalPathNormalizer].
 *
 * `android.net.Uri` is stubbed via MockK because robolectric is not on this module's
 * test classpath - the normalizer only uses `Uri.parse(..)` + `path`/`authority` and `Uri.decode(..)`.
 *
 * S3769: LOCAL non-content paths are resolved by pure string manipulation (no File.canonicalPath),
 * so all assertions are platform-independent.
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
        every { Uri.parse("content://media/external/images/12345") } returns mockContentUri("/external/images/12345", "media")
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
    fun `LOCAL content URI keeps scheme and authority`() {
        // S3770: the authority is part of the identity, so the canonical form keeps it.
        val out = normalizer.canonical("content://media/external/images/12345", ResourceType.LOCAL)
        assertEquals("content://media/external/images/12345", out)
    }

    @Test
    fun `LOCAL content URIs from two providers with same path differ`() {
        // S3770: path-only identities collided across providers and let the reconciler remove
        // the wrong row; the authority must discriminate them.
        every { Uri.parse("content://providerA/root/x.jpg") } returns mockContentUri("/root/x.jpg", "providerA")
        every { Uri.parse("content://providerB/root/x.jpg") } returns mockContentUri("/root/x.jpg", "providerB")
        val a = normalizer.canonical("content://providerA/root/x.jpg", ResourceType.LOCAL)
        val b = normalizer.canonical("content://providerB/root/x.jpg", ResourceType.LOCAL)
        assertNotEquals(a, b)
        assertEquals("content://providerA/root/x.jpg", a)
        assertEquals("content://providerB/root/x.jpg", b)
    }

    @Test
    fun `LOCAL dot-dot segment is folded by string resolution`() {
        val a = normalizer.canonical("/sdcard/DCIM/../DCIM/IMG.jpg", ResourceType.LOCAL)
        val b = normalizer.canonical("/sdcard/DCIM/IMG.jpg", ResourceType.LOCAL)
        assertEquals(b, a)
    }

    @Test
    fun `LOCAL dot-dot past root stays at root`() {
        val out = normalizer.canonical("/sdcard/../../file.txt", ResourceType.LOCAL)
        assertEquals("/file.txt", out)
    }

    @Test
    fun `LOCAL dot segment is removed`() {
        val out = normalizer.canonical("/sdcard/./DCIM/./IMG.jpg", ResourceType.LOCAL)
        assertEquals("/sdcard/DCIM/IMG.jpg", out)
    }

    @Test
    fun `LOCAL double slash is collapsed`() {
        val out = normalizer.canonical("/sdcard//DCIM//IMG.jpg", ResourceType.LOCAL)
        assertEquals("/sdcard/DCIM/IMG.jpg", out)
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
        // S3769: all cases are platform-independent (pure string resolution for LOCAL).
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

    private fun mockContentUri(pathPart: String, authority: String): Uri {
        val uri = io.mockk.mockk<Uri>()
        every { uri.path } returns pathPart
        every { uri.authority } returns authority
        return uri
    }
}
