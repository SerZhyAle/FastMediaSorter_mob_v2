package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpCookie

/**
 * S0174 Phase 03: Unit tests for [CookieFileWriter] Netscape serialisation, eTLD+1 matching,
 * and file lifecycle (write + delete).
 *
 * Uses mockk for [EncryptedCookieStore] to avoid AndroidKeyStore dependency, and Robolectric
 * for a real [Context.filesDir] path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CookieFileWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var store: EncryptedCookieStore
    private lateinit var writer: CookieFileWriter

    @Before
    fun setUp() {
        store = mockk()
        val context = RuntimeEnvironment.getApplication()
        writer = CookieFileWriter(context, store)
    }

    @Test
    fun writeCookieFile_noMatchingCookies_returnsNull() {
        // Different eTLD+1: store has "facebook.com", target is "instagram.com"
        val entry = EncryptedCookieStore.AccountEntry(
            accountId = "acc1",
            displayName = "Facebook",
            savedAt = null,
            lastUsedAt = null,
            cookieCount = 1,
            type = EncryptedCookieStore.TYPE_ACTIVE,
        )
        every { store.listAllAccounts() } returns listOf("www.facebook.com" to entry)

        val result = writer.writeCookieFile("www.instagram.com")

        assertNull(result)
    }

    @Test
    fun writeCookieFile_matchingCookies_writesNetscapeFile() {
        val cookie = HttpCookie("sessionid", "abc123").apply {
            domain = ".instagram.com"
            path = "/"
            secure = true
            maxAge = 3600L
        }
        val entry = EncryptedCookieStore.AccountEntry(
            accountId = "acc1",
            displayName = "Instagram",
            savedAt = null,
            lastUsedAt = null,
            cookieCount = 1,
            type = EncryptedCookieStore.TYPE_ACTIVE,
        )
        every { store.listAllAccounts() } returns listOf("www.instagram.com" to entry)
        every { store.loadForAccount("www.instagram.com", "acc1") } returns listOf(cookie)

        val file = writer.writeCookieFile("www.instagram.com")

        assertNotNull(file)
        assertTrue(file!!.exists())
        val content = file.readText()
        assertTrue("Header present", content.contains("# Netscape HTTP Cookie File"))
        assertTrue("Cookie name present", content.contains("sessionid"))
        // Cookie value should be in a tab-separated line
        val cookieLine = content.lines().firstOrNull { it.contains("sessionid") }
        assertNotNull(cookieLine)
        val parts = cookieLine!!.split("\t")
        assertEquals(7, parts.size)
        assertEquals("abc123", parts[6].trim()) // value is last field
        file.delete()
    }

    @Test
    fun deleteCookieFile_deletesFile() {
        val file = tempFolder.newFile("test_cookie.txt")
        file.writeText("dummy")
        assertTrue(file.exists())

        writer.deleteCookieFile(file)

        assertFalse(file.exists())
    }
}
