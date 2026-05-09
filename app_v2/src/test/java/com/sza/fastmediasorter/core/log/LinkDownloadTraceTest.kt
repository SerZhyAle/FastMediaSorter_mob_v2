package com.sza.fastmediasorter.core.log

import androidx.test.core.app.ApplicationProvider
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * S0116 Phase 07 step 4: contract test for [LinkDownloadTrace].
 * Robolectric is required because `truncateUrl` uses Android's `Uri.parse`.
 */
@RunWith(RobolectricTestRunner::class)
class LinkDownloadTraceTest {

    @Test
    fun `truncateUrl strips query and fragment, keeps host plus first two path segments`() {
        // Initialise app context so static Android calls resolve under Robolectric.
        ApplicationProvider.getApplicationContext<android.content.Context>()
        val out = LinkDownloadTrace.truncateUrl(
            "https://example.com/a/b/c/d?token=secret#hash",
        )
        assertEquals("https://example.com/a/b", out)
    }

    @Test
    fun `truncateUrl returns sentinel for blank input`() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("<invalid_url>", LinkDownloadTrace.truncateUrl(""))
    }

    @Test
    fun `truncateUrl returns sentinel for relative path with no scheme`() {
        ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("<invalid_url>", LinkDownloadTrace.truncateUrl("not a url"))
    }

    @Test
    fun `truncateCookies emits count and names but never values`() {
        val cookies = listOf(
            Cookie.Builder().name("session").value("s3cret").domain("example.com").build(),
            Cookie.Builder().name("csrf").value("v4lue").domain("example.com").build(),
        )
        val out = LinkDownloadTrace.truncateCookies(cookies)
        assertEquals("count=2 names=[session,csrf]", out)
        assertFalse("cookie values must not appear in trace text", out.contains("s3cret"))
        assertFalse("cookie values must not appear in trace text", out.contains("v4lue"))
    }
}
