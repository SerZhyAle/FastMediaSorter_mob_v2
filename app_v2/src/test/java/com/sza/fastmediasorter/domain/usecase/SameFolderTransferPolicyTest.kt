package com.sza.fastmediasorter.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** MEDIA-VIEWER rule 4 conformance: the same source and destination folder is refused, others are not. */
class SameFolderTransferPolicyTest {

    private val saf = "content://com.android.externalstorage.documents/tree"
    private val dcim = "/storage/emulated/0/DCIM"

    private fun same(source: String, destination: String) =
        SameFolderTransferPolicy.isSameFolder(source, destination)

    @Test
    fun `local file into its own folder is the same folder`() {
        assertTrue(same("$dcim/a.jpg", dcim))
        assertTrue(same("$dcim/a.jpg", "$dcim/"))
        assertTrue(same("file:///sdcard/Pics/a.jpg", "/sdcard/Pics"))
    }

    @Test
    fun `local file into another folder is not the same folder`() {
        assertFalse(same("$dcim/a.jpg", "$dcim/Sorted"))
        assertFalse(same("$dcim/a.jpg", "/storage/emulated/0"))
    }

    @Test
    fun `network share compares the full folder`() {
        assertTrue(same("smb://nas/share/photos/a.jpg", "smb://nas/share/photos"))
        assertFalse(same("smb://nas/share/photos/a.jpg", "smb://nas/share/other"))
    }

    @Test
    fun `saf document into its own tree is the same folder`() {
        val doc = "$saf/primary%3ADCIM/document/primary%3ADCIM%2Fa.jpg"
        assertTrue(same(doc, "$saf/primary%3ADCIM"))
        assertFalse(same(doc, "$saf/primary%3AMusic"))
    }

    @Test
    fun `saf document at the volume root matches the root tree`() {
        val doc = "$saf/primary%3A/document/primary%3Aa.jpg"
        assertTrue(same(doc, "$saf/primary%3A"))
    }

    @Test
    fun `different schemes and blank input are never the same folder`() {
        assertFalse(same("/sdcard/a.jpg", "content://x/tree/primary%3A"))
        assertFalse(same("", "/sdcard"))
        assertFalse(same("/sdcard/a.jpg", ""))
    }
}
