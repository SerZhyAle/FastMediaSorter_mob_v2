package com.sza.fastmediasorter.core.share

import android.net.Uri
import com.sza.fastmediasorter.domain.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the S0493 remote-source marker on [ShareableContent]. */
class ShareableContentMaterializationTest {

    private fun content(uris: List<Uri> = emptyList(), sourcePath: String? = null) =
        ShareableContent(uris = uris, mime = "audio/*", mediaType = MediaType.AUDIO, sourcePath = sourcePath)

    @Test
    fun `remote source with no local uri requires materialization`() {
        assertTrue(content(sourcePath = "smb://host/share/a.mp3").requiresMaterialization)
        assertTrue(content(sourcePath = "sftp://host/a.mp3").requiresMaterialization)
        assertTrue(content(sourcePath = "ftp://host/a.mp3").requiresMaterialization)
    }

    @Test
    fun `local or absent source does not require materialization`() {
        assertFalse(content(sourcePath = "/storage/emulated/0/a.mp3").requiresMaterialization)
        assertFalse(content(sourcePath = null).requiresMaterialization)
    }
}
