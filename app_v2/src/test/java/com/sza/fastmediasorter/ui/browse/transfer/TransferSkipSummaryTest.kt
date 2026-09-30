package com.sza.fastmediasorter.ui.browse.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransferSkipSummaryTest {

    @Test
    fun `local and network paths reduce to the bare name`() {
        val names = TransferSkipSummary.displayNames(
            listOf("/storage/emulated/0/DCIM/a.jpg", "smb://host/share/dir/b c.png", "sftp://h/x/"),
        )
        assertEquals(listOf("a.jpg", "b c.png", "x"), names)
    }

    @Test
    fun `saf document uris are percent decoded and keep a literal plus`() {
        val names = TransferSkipSummary.displayNames(
            listOf(
                "content://com.android.externalstorage.documents/tree/primary%3ADCIM/document/" +
                    "primary%3ADCIM%2Fa+b%20c.jpg",
                "content://com.android.externalstorage.documents/document/primary%3Aroot.jpg",
            ),
        )
        assertEquals(listOf("a+b c.jpg", "root.jpg"), names)
    }

    @Test
    fun `a malformed escape keeps the raw tail`() {
        assertEquals(listOf("50%zz.jpg"), TransferSkipSummary.displayNames(listOf("content://p/doc/50%zz.jpg")))
    }

    @Test
    fun `nothing skipped yields no listing`() {
        assertNull(TransferSkipSummary.listing(0, emptyList()))
    }

    @Test
    fun `listing caps the names and counts the rest`() {
        val names = (1..7).map { "f$it.jpg" }
        assertEquals("f1.jpg, f2.jpg, f3.jpg, f4.jpg, f5.jpg +2", TransferSkipSummary.listing(7, names))
        assertEquals("f1.jpg, f2.jpg", TransferSkipSummary.listing(2, names.take(2)))
    }

    @Test
    fun `a count without names still reports the count`() {
        assertEquals("3", TransferSkipSummary.listing(3, emptyList()))
    }
}
