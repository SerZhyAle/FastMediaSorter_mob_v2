package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The metadata rules of contract DEVICE-EXCHANGE 22 item BW, which make a broken `clipboard-put` unbuildable. */
class ExchangeClipboardValueTest {

    @Test
    fun `text carries neither a type nor a name`() {
        ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, 1, null, null)

        assertThrows(IllegalArgumentException::class.java) {
            ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, 1, "text/plain", null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, 1, null, "a.txt")
        }
    }

    @Test
    fun `an image needs its type and may go without a name`() {
        ExchangeClipboardMeta(ExchangeClipboardKind.IMAGE, 1, "image/png", null)

        assertThrows(IllegalArgumentException::class.java) {
            ExchangeClipboardMeta(ExchangeClipboardKind.IMAGE, 1, null, null)
        }
    }

    @Test
    fun `a file needs both its type and its name`() {
        ExchangeClipboardMeta(ExchangeClipboardKind.FILE, 1, "application/octet-stream", "report.pdf")

        assertThrows(IllegalArgumentException::class.java) {
            ExchangeClipboardMeta(ExchangeClipboardKind.FILE, 1, "application/pdf", null)
        }
    }

    @Test
    fun `an empty value is not a value`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeClipboardMeta(ExchangeClipboardKind.TEXT, 0, null, null)
        }
    }

    @Test
    fun `a name with a separator, a control character or a dot form is refused`() {
        assertFalse(ExchangeClipboardMeta.isValidName("a/b"))
        assertFalse(ExchangeClipboardMeta.isValidName("a\\b"))
        assertFalse(ExchangeClipboardMeta.isValidName("a\u0007b"))
        assertFalse(ExchangeClipboardMeta.isValidName("."))
        assertFalse(ExchangeClipboardMeta.isValidName(".."))
        assertFalse(ExchangeClipboardMeta.isValidName("x".repeat(LONG_NAME)))
        assertTrue(ExchangeClipboardMeta.isValidName("Отчёт 2026.pdf"))
    }

    @Test
    fun `a type must be one type and one subtype`() {
        assertTrue(ExchangeClipboardMeta.isValidMimeType("image/png"))
        assertFalse(ExchangeClipboardMeta.isValidMimeType("image"))
        assertFalse(ExchangeClipboardMeta.isValidMimeType("image/png/x"))
        assertFalse(ExchangeClipboardMeta.isValidMimeType("a/" + "b".repeat(LONG_NAME)))
    }

    @Test
    fun `the stored time parses and a malformed one is null`() {
        assertEquals(1_760_000_000L, value("2025-10-09T08:53:20Z").storedAtInstant()?.epochSecond)
        assertNull(value("yesterday").storedAtInstant())
    }

    @Test
    fun `the printed form carries no name, hash or id`() {
        val printed = value("2025-10-09T08:53:20Z").toString()

        assertFalse(printed.contains(NAME))
        assertFalse(printed.contains(SHA))
        assertFalse(printed.contains(CLIP_ID))
    }

    private fun value(storedAt: String) = ExchangeClipboardValue(
        clipId = CLIP_ID,
        kind = ExchangeClipboardKind.FILE,
        size = 3,
        sha256 = SHA,
        storedAt = storedAt,
        sourceDeviceId = null,
        sourceDeviceName = "Laptop",
        mimeType = "application/pdf",
        name = NAME,
    )

    private companion object {
        const val LONG_NAME = 256
        const val CLIP_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        const val SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        const val NAME = "secret-plan.pdf"
    }
}
