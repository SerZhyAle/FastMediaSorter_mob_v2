package com.sza.fastmediasorter.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafHelperDocumentPathTest {

    @Test
    fun `primary id maps under primary storage`() {
        assertEquals("/storage/emulated/0/DCIM/a.jpg", SafHelper.documentIdToFilePath("primary:DCIM/a.jpg"))
    }

    @Test
    fun `removable volume id is not mapped onto primary storage`() {
        assertNull(SafHelper.documentIdToFilePath("ABCD-1234:DCIM/a.jpg"))
    }

    @Test
    fun `msf id has no resolvable path`() {
        assertNull(SafHelper.documentIdToFilePath("msf:1234"))
    }

    @Test
    fun `raw id yields its absolute path`() {
        assertEquals(
            "/storage/emulated/0/Download/a.jpg",
            SafHelper.documentIdToFilePath("raw:/storage/emulated/0/Download/a.jpg"),
        )
    }

    @Test
    fun `raw id without absolute path is ignored`() {
        assertNull(SafHelper.documentIdToFilePath("raw:relative/a.jpg"))
    }
}
