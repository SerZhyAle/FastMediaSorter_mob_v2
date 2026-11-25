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

    @Test
    fun `formatDisplayPath converts primary SAF tree URI to storage path`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FFastMediaSorter_Walk%2Fsrc"
        assertEquals(
            "/storage/emulated/0/Download/FastMediaSorter_Walk/src",
            SafHelper.formatDisplayPath(uri),
        )
    }

    @Test
    fun `formatDisplayPath handles primary root tree URI`() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3A"
        assertEquals("/storage/emulated/0", SafHelper.formatDisplayPath(uri))
    }

    @Test
    fun `formatDisplayPath prefers document id in tree-document URI and keeps plus signs`() {
        val uri = "content://com.android.externalstorage.documents/tree/" +
            "primary%3ADownload/document/primary%3ADownload%2FSub%2Ba"
        assertEquals("/storage/emulated/0/Download/Sub+a", SafHelper.formatDisplayPath(uri))
    }

    @Test
    fun `formatDisplayPath formats removable volume tree URI cleanly`() {
        val uri = "content://com.android.externalstorage.documents/tree/ABCD-1234%3ADCIM%2FCamera"
        assertEquals("ABCD-1234: DCIM/Camera", SafHelper.formatDisplayPath(uri))
    }

    @Test
    fun `formatDisplayPath converts raw document id inside tree URI`() {
        val uri = "content://com.android.externalstorage.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FDownload"
        assertEquals("/storage/emulated/0/Download", SafHelper.formatDisplayPath(uri))
    }

    @Test
    fun `formatDisplayPath leaves non-content paths unchanged`() {
        assertEquals("/storage/emulated/0/Download", SafHelper.formatDisplayPath("/storage/emulated/0/Download"))
        assertEquals("virtual://recent", SafHelper.formatDisplayPath("virtual://recent"))
        assertEquals("smb://192.168.1.1/media", SafHelper.formatDisplayPath("smb://192.168.1.1/media"))
    }
}
