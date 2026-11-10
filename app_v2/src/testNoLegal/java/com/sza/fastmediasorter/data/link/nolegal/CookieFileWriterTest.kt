package com.sza.fastmediasorter.data.link.nolegal

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
import java.util.concurrent.TimeUnit

/**
 * S0174 Phase 03: Unit tests for [CookieFileWriter] Netscape serialisation and file lifecycle
 * (write + delete). S4511: the writer serialises exactly the cookie list it is given.
 *
 * Robolectric supplies a real [Context.filesDir] path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CookieFileWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var writer: CookieFileWriter

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        writer = CookieFileWriter(context)
    }

    @Test
    fun writeCookieFile_emptyCookieList_returnsNull() {
        val result = writer.writeCookieFile("www.instagram.com", emptyList())

        assertNull(result)
    }

    @Test
    fun writeCookieFile_oneSessionList_writesOnlyThoseCookies() {
        // S4511: the file carries the picked account's single cookie, never another account's set.
        val picked = HttpCookie("csrftoken", "picked-token").apply {
            domain = ".instagram.com"
            path = "/"
        }

        val file = writer.writeCookieFile("www.instagram.com", listOf(picked))

        assertNotNull(file)
        val cookieLines = file!!.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(1, cookieLines.size)
        assertTrue(cookieLines.single().contains("csrftoken"))
        assertFalse(file.readText().contains("sessionid"))
        file.delete()
    }

    @Test
    fun writeCookieFile_sameNameOnTwoPaths_keepsBothLines() {
        val root = HttpCookie("rur", "a").apply {
            domain = ".instagram.com"
            path = "/"
        }
        val nested = HttpCookie("rur", "b").apply {
            domain = ".instagram.com"
            path = "/api"
        }

        val file = writer.writeCookieFile("www.instagram.com", listOf(root, nested))

        assertNotNull(file)
        val cookieLines = file!!.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(2, cookieLines.size)
        file.delete()
    }

    @Test
    fun writeCookieFile_matchingCookies_writesNetscapeFile() {
        val cookie = HttpCookie("sessionid", "abc123").apply {
            domain = ".instagram.com"
            path = "/"
            secure = true
            maxAge = 3600L
        }

        val file = writer.writeCookieFile("www.instagram.com", listOf(cookie))

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
        // S0822 regression: field 4 (expiry) must be an ABSOLUTE epoch-seconds timestamp
        // (now + maxAge), not the raw relative maxAge. A bare "3600" reads as 1970 and yt-dlp
        // drops the cookie as expired, breaking authenticated fetches.
        val expirySeconds = parts[EXPIRY_FIELD_INDEX].toLong()
        val nowSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        assertTrue(
            "expiry must be a future absolute epoch, was $expirySeconds",
            expirySeconds > nowSeconds,
        )
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

    private companion object {
        // Netscape cookie line: domain, includeSubdomain, path, secure, expiry, name, value.
        const val EXPIRY_FIELD_INDEX = 4
    }
}
