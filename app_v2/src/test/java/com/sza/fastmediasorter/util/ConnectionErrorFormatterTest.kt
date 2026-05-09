package com.sza.fastmediasorter.util

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockk
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S0118 — unit tests for [ConnectionErrorFormatter].
 *
 * Resource string lookups are stubbed so the asserts focus on the shape of the
 * returned [Pair]/[com.sza.fastmediasorter.ui.common.copy.UiMessageSpec] rather
 * than on translated copy.
 */
class ConnectionErrorFormatterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true) {
            every { getString(any<Int>()) } returns "stub"
            every { getString(any<Int>(), *anyVararg()) } returns "stub-with-args"
        }
    }

    private fun smbResource(): MediaResource = MediaResource(
        name = "Backup",
        path = "smb://192.168.1.10/share",
        type = ResourceType.SMB,
    )

    @Test
    fun `authentication failure - returns user message and optional technical details`() {
        val error = RuntimeException("STATUS_LOGON_FAILURE: bad credentials")
        val (userMessage, details) = ConnectionErrorFormatter.formatConnectionError(
            context = context,
            resource = smbResource(),
            error = error,
            showTechnicalDetails = true,
        )
        assertNotNull(userMessage)
        assertTrue(userMessage.isNotBlank())
        assertNotNull(details)
    }

    @Test
    fun `timeout exception - emoji-free user message`() {
        val error = SocketTimeoutException("Connect timed out after 5000ms")
        val (userMessage, _) = ConnectionErrorFormatter.formatConnectionError(
            context = context,
            resource = smbResource(),
            error = error,
            showTechnicalDetails = false,
        )
        for (emoji in EMOJI_BLACKLIST) {
            assertFalse(
                "Timeout user message contains emoji '$emoji'",
                userMessage.contains(emoji),
            )
        }
    }

    @Test
    fun `generic fallback - returns spec with non-blank short message`() {
        val spec = ConnectionErrorFormatter.formatAsSpec(
            context = context,
            resource = smbResource(),
            error = IllegalStateException("unrecognised connection error"),
            showTechnicalDetails = false,
        )
        assertTrue(spec.shortMessage.isNotBlank())
    }

    private companion object {
        private val EMOJI_BLACKLIST = listOf("❌", "⚠", "💡", "📄", "📂", "📁", "🔧")
    }
}
