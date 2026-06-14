package com.sza.fastmediasorter.data.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0200 Step 03.6: covers [GoogleDomainMatcher] host-matching logic.
 *
 * Robolectric is used only because `android.net.Uri.parse` requires the Android runtime even when
 * the matcher itself contains no other Android dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
class GoogleDomainMatcherTest {

    @Test
    fun `accounts_google_com matches`() {
        assertTrue(GoogleDomainMatcher.isGoogleAuthUrl("https://accounts.google.com/signin"))
    }

    @Test
    fun `subdomain of google_com matches`() {
        assertTrue(GoogleDomainMatcher.isGoogleAuthUrl("https://myaccount.google.com/security"))
    }

    @Test
    fun `googleusercontent_com does not match`() {
        assertFalse(GoogleDomainMatcher.isGoogleAuthUrl("https://lh3.googleusercontent.com/a/photo"))
    }

    @Test
    fun `youtube_com matches`() {
        assertTrue(GoogleDomainMatcher.isGoogleAuthUrl("https://youtube.com/watch?v=X"))
    }

    @Test
    fun `music_youtube_com matches`() {
        assertTrue(GoogleDomainMatcher.isGoogleAuthUrl("https://music.youtube.com/browse"))
    }

    @Test
    fun `youtu_be short link does not match`() {
        // youtu.be is YouTube's short-link host, not an auth/OAuth surface.
        assertFalse(GoogleDomainMatcher.isGoogleAuthUrl("https://youtu.be/abc123"))
    }

    @Test
    fun `empty string does not match`() {
        assertFalse(GoogleDomainMatcher.isGoogleAuthUrl(""))
    }

    @Test
    fun `null url does not match`() {
        assertFalse(GoogleDomainMatcher.isGoogleAuthUrl(null))
    }
}
