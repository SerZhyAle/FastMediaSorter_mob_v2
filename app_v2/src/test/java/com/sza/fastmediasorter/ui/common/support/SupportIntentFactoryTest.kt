package com.sza.fastmediasorter.ui.common.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.core.util.LocaleHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S0118 - unit tests for [SupportIntentFactory].
 *
 * Coverage:
 * - Localized help URL path (EN, RU, UK).
 * - Bug-report email path (mailto target + subject).
 * - Review destination path (Play Store URI).
 *
 * Robolectric: the factory builds real Intent/Uri instances, which are unstubbed on the plain JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.11.1 maxSdkVersion=34; targetSdk 35 needs the explicit pin.
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

    // ── buildCrashReportEmail ───────────────────────────────────────────────

    @Test
    fun `buildCrashReportEmail - with attachment carries recipient, subject, body and stream`() {
        val attachment = Uri.parse("content://com.sza.fastmediasorter.fileprovider/cache/logs.zip")
        val intent = SupportIntentFactory.buildCrashReportEmail(
            subject = "Crash",
            body = "stack",
            attachmentUri = attachment,
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/zip", intent.type)
        val recipients = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        assertNotNull(recipients)
        assertTrue("Recipient must be the crash-report address", recipients!!.contains("serzhyale@gmail.com"))
        assertEquals("Crash", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("stack", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue("Attachment intent must carry a stream", intent.hasExtra(Intent.EXTRA_STREAM))
        assertTrue(
            "Attachment intent must grant read permission",
            (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
        )
    }

    @Test
    fun `buildCrashReportEmail - without attachment is plain text and has no stream`() {
        val intent = SupportIntentFactory.buildCrashReportEmail(
            subject = "Crash",
            body = "stack",
            attachmentUri = null,
        )
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertFalse("No attachment must mean no stream", intent.hasExtra(Intent.EXTRA_STREAM))
    }
}
