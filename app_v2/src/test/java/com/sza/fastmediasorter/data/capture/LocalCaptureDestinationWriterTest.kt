package com.sza.fastmediasorter.data.capture

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class LocalCaptureDestinationWriterTest {

    private val writer = LocalCaptureDestinationWriter(
        mock(Context::class.java),
        LocalDestinationClassifier(),
        mock(LocalDestinationWriter::class.java),
    )

    @Test
    fun `filesystem path does not select SAF`() {
        assertFalse(writer.isSafDestination("/storage/emulated/0/Movies"))
    }

    @Test
    fun `canonical content URI selects SAF`() {
        assertTrue(writer.isSafDestination("content://provider/tree/primary%3AMovies"))
    }

    @Test
    fun `malformed content URI normalizes before SAF access`() {
        assertEquals(
            "content://provider/tree/primary%3AMovies",
            writer.normalizeSafDestination("content:/provider/tree/primary%3AMovies"),
        )
    }
}
