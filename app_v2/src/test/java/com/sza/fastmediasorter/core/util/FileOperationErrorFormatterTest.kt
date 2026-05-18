package com.sza.fastmediasorter.core.util

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0118 - unit tests for [FileOperationErrorFormatter].
 *
 * The formatter pulls user-visible reason strings from Android resources, so the
 * tests stub `Context.getString()` to return the resource id back as a literal.
 * That keeps the asserts focused on shape (no emoji prefixes, primary copy is
 * short, technical detail comes through) rather than on translation content.
 */
class FileOperationErrorFormatterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true) {
            // Resource lookups: return a deterministic placeholder for the message
            // body so the test can detect emoji-stripping without depending on the
            // real localized text.
            every { getString(any<Int>()) } returns "stub-reason"
            every { getString(any<Int>(), *anyVararg()) } returns "stub-with-args"
        }
    }

    @Test
    fun `authentication failure - has no emoji prefixes in primary copy`() {
        val result = FileOperationErrorFormatter.formatError(
            context = context,
            fileName = "report.docx",
            operation = "copy",
            errorMessage = "STATUS_LOGON_FAILURE: authentication failed",
            showDetailedErrors = false,
        )

        assertNotNull(result)
        // Primary user copy must not start with the legacy emoji prefixes.
        for (emoji in EMOJI_BLACKLIST) {
            assertFalse(
                "FileOperationErrorFormatter primary copy still contains emoji '$emoji': $result",
                result.contains(emoji),
            )
        }
    }

    @Test
    fun `timeout error - reaches the formatter without emoji decoration`() {
        val result = FileOperationErrorFormatter.formatError(
            context = context,
            fileName = "video.mp4",
            operation = "move",
            errorMessage = "java.net.SocketTimeoutException: read timed out",
            showDetailedErrors = false,
        )

        assertTrue(result.isNotBlank())
        for (emoji in EMOJI_BLACKLIST) {
            assertFalse(
                "Timeout copy contains emoji '$emoji'",
                result.contains(emoji),
            )
        }
    }

    @Test
    fun `generic fallback - emoji-free output for unknown error type`() {
        val result = FileOperationErrorFormatter.formatError(
            context = context,
            fileName = "data.bin",
            operation = "delete",
            errorMessage = "weird unrecognised error blob",
            showDetailedErrors = true,
            sourcePath = "/sdcard/Documents/data.bin",
        )

        for (emoji in EMOJI_BLACKLIST) {
            assertFalse(
                "Generic fallback copy contains emoji '$emoji'",
                result.contains(emoji),
            )
        }
        assertTrue("Detailed mode should keep the file name in scope", result.contains("data.bin"))
    }

    @Test
    fun `formatAsSpec - returns a UiMessageSpec with non-blank short message`() {
        val spec = FileOperationErrorFormatter.formatAsSpec(
            context = context,
            fileName = "image.jpg",
            operation = "copy",
            errorMessage = "STATUS_ACCESS_DENIED: permission rejected",
            sourcePath = "/sdcard/Pictures/image.jpg",
            destPath = "/sdcard/Backups/image.jpg",
        )
        assertTrue(spec.shortMessage.isNotBlank())
    }

    private companion object {
        private val EMOJI_BLACKLIST = listOf("❌", "⚠", "💡", "📄", "📂", "📁", "🔧")
    }
}
