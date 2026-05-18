package com.sza.fastmediasorter.ui.common.support

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.util.LocaleHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0118 - unit tests for [SupportIntentFactory].
 *
 * Coverage:
 * - Localized help URL path (EN, RU, UK).
 * - Bug-report email path (mailto target + subject).
 * - Review destination path (Play Store URI).
 */
class SupportIntentFactoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true) {
            every { packageName } returns "com.sza.fastmediasorter"
        }
        mockkObject(LocaleHelper)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── helpUrl ─────────────────────────────────────────────────────────────

    @Test
    fun `helpUrl - returns RU localized URL when locale is ru`() {
        every { LocaleHelper.getLanguage(context) } returns "ru"
        val url = SupportIntentFactory.helpUrl(context)
        assertTrue("URL must mention RU index: $url", url.contains("index-ru"))
    }

    @Test
    fun `helpUrl - returns UK localized URL when locale is uk`() {
        every { LocaleHelper.getLanguage(context) } returns "uk"
        val url = SupportIntentFactory.helpUrl(context)
        assertTrue("URL must mention UK index: $url", url.contains("index-uk"))
    }

    @Test
    fun `helpUrl - falls back to EN base when locale is unknown`() {
        every { LocaleHelper.getLanguage(context) } returns "fr"
        val url = SupportIntentFactory.helpUrl(context)
        assertTrue("URL must point at the EN docs base: $url", url.endsWith("/docs/howto/"))
    }

    // ── REPORT_PROBLEM ──────────────────────────────────────────────────────

    @Test
    fun `build REPORT_PROBLEM - produces a SENDTO mailto intent`() {
        every { LocaleHelper.getLanguage(context) } returns "en"
        val intent = SupportIntentFactory.build(
            context = context,
            destination = SupportDestination.REPORT_PROBLEM,
            emailSubject = "Bug",
        )
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        val uri = intent.data
        assertNotNull(uri)
        assertEquals("mailto", uri!!.scheme)
        assertTrue("Recipient must be the canonical bug-report address", uri.toString().contains("sza@ukr.net"))
        assertEquals("Bug", intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    // ── HELP ────────────────────────────────────────────────────────────────

    @Test
    fun `build HELP - produces an ACTION_VIEW intent for the help URL`() {
        every { LocaleHelper.getLanguage(context) } returns "en"
        val intent = SupportIntentFactory.build(
            context = context,
            destination = SupportDestination.HELP,
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val url = intent.data?.toString().orEmpty()
        assertTrue("HELP intent must point at the help URL: $url", url.contains("/docs/howto"))
    }

    // ── LEAVE_FEEDBACK ──────────────────────────────────────────────────────

    @Test
    fun `build LEAVE_FEEDBACK - prefers market URI for the Play Store app`() {
        every { LocaleHelper.getLanguage(context) } returns "en"
        val intent = SupportIntentFactory.build(
            context = context,
            destination = SupportDestination.LEAVE_FEEDBACK,
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val uri = intent.data?.toString().orEmpty()
        assertTrue("LEAVE_FEEDBACK must use market://: $uri", uri.startsWith("market://"))
        assertTrue("LEAVE_FEEDBACK must reference our package id: $uri", uri.contains("com.sza.fastmediasorter"))
    }

    @Test
    fun `leaveFeedbackWebFallback - returns a play-google-com URL`() {
        val intent = SupportIntentFactory.leaveFeedbackWebFallback(context)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val uri = intent.data?.toString().orEmpty()
        assertTrue("Web fallback must point at play.google.com: $uri", uri.contains("play.google.com"))
    }
}
