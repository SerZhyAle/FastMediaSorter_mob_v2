package com.sza.fastmediasorter.core.clipboard

import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The received-file naming of contract DEVICE-EXCHANGE 22 item CB. */
class ExchangeClipboardFileNamesTest {

    @Test
    fun `only the base name of a path survives`() {
        assertEquals("passwd", name("../../etc/passwd"))
        assertEquals("boot.ini", name("C:\\Windows\\..\\boot.ini"))
    }

    @Test
    fun `characters a file system refuses become underscores`() {
        assertEquals("a_b_c_.txt", name("a<b>c?.txt"))
        assertEquals("tab_name.pdf", name("tab\tname.pdf"))
    }

    @Test
    fun `an empty or dot name falls back to a made-up one`() {
        assertEquals("clipboard-20261014100000.bin", name(""))
        assertEquals("clipboard-20261014100000.bin", name(".."))
    }

    @Test
    fun `an image with no name is named from its time and type`() {
        val made = ExchangeClipboardFileNames.safeName(null, ExchangeClipboardKind.IMAGE, "image/png", STORED_AT)

        assertEquals("clipboard-20261014100000.png", made)
    }

    @Test
    fun `text is always saved as a txt file`() {
        val made = ExchangeClipboardFileNames.safeName(null, ExchangeClipboardKind.TEXT, null, STORED_AT)

        assertEquals("clipboard-20261014100000.txt", made)
    }

    @Test
    fun `a taken name gets the next free suffix before its extension`() {
        val taken = setOf("report.pdf", "report (2).pdf")

        assertEquals("report (3).pdf", ExchangeClipboardFileNames.uniqueIn(taken, "report.pdf"))
        assertEquals("notes", ExchangeClipboardFileNames.uniqueIn(taken, "notes"))
        assertEquals("archive (2)", ExchangeClipboardFileNames.uniqueIn(setOf("archive"), "archive"))
    }

    private fun name(raw: String) =
        ExchangeClipboardFileNames.safeName(raw, ExchangeClipboardKind.FILE, "application/octet-stream", STORED_AT)

    private companion object {
        const val STORED_AT = "2026-10-14T10:00:00Z"
    }
}
