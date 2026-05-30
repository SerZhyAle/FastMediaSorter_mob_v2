package com.sza.fastmediasorter.data.browser

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [GoogleDomainBrowserLauncher.routeAuthUrl] host routing and the
 * CctUnavailableException path: non-Google URLs go to the WebView fallback (no CCT touched);
 * Google URLs route to launch(), which throws when no CCT browser is available. CctAvailabilityChecker
 * is mocked - the successful CustomTabsIntent launch needs a real Activity (instrumentation), out of scope.
 * Robolectric supplies android.net.Uri for the GoogleDomainMatcher host decision.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDomainBrowserLauncherTest {

    private val cctChecker = mockk<CctAvailabilityChecker>()
    private val appContext = mockk<Context>(relaxed = true)
    private val activityContext = mockk<Context>(relaxed = true)
    private val launcher = GoogleDomainBrowserLauncher(appContext, cctChecker)

    @Test
    fun `non-Google url routes to webview fallback`() {
        var fallbackUrl: String? = null
        launcher.routeAuthUrl(activityContext, "https://instagram.com/p/abc") { fallbackUrl = it }

        assertEquals("https://instagram.com/p/abc", fallbackUrl)
        // CCT must not be consulted for non-Google hosts.
        verify(exactly = 0) { cctChecker.resolveCctPackage() }
    }

    @Test
    fun `google url with no CCT browser throws CctUnavailableException`() {
        every { cctChecker.resolveCctPackage() } returns null

        val ex = runCatching {
            launcher.routeAuthUrl(activityContext, "https://accounts.google.com/o/oauth2/auth") { }
        }.exceptionOrNull()

        assertTrue(ex is CctUnavailableException)
    }

    @Test
    fun `launch throws CctUnavailableException when no browser`() {
        every { cctChecker.resolveCctPackage() } returns null
        val ex = runCatching {
            launcher.launch(activityContext, "https://accounts.google.com/o/oauth2/auth")
        }.exceptionOrNull()
        assertTrue(ex is CctUnavailableException)
    }
}
