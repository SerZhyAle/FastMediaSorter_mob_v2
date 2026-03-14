package com.sza.fastmediasorter.domain.usecase.scan

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [IncrementalScanStrategy] — specifically the virtual-path guard
 * in [IncrementalScanStrategy.currentFolderMtime].
 */
class IncrementalScanStrategyTest {

    @After
    fun tearDown() {
        IncrementalScanStrategy.reset()
    }

    @Test
    fun `currentFolderMtime returns null for virtual recent path`() {
        assertNull(IncrementalScanStrategy.currentFolderMtime(LocalMediaScanner.VIRTUAL_PATH_RECENT))
    }

    @Test
    fun `currentFolderMtime returns null for virtual all_audio path`() {
        assertNull(IncrementalScanStrategy.currentFolderMtime(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO))
    }

    @Test
    fun `currentFolderMtime returns null for virtual all_video path`() {
        assertNull(IncrementalScanStrategy.currentFolderMtime(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO))
    }

    @Test
    fun `currentFolderMtime returns null for virtual all_docs path`() {
        assertNull(IncrementalScanStrategy.currentFolderMtime(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS))
    }

    @Test
    fun `currentFolderMtime returns null for non-existent physical path`() {
        assertNull(IncrementalScanStrategy.currentFolderMtime("/non/existent/path/xyz123"))
    }
}
