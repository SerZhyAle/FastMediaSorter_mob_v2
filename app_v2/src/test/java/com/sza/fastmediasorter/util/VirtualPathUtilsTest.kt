package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualPathUtilsTest {

    @Test
    fun `isVirtualPath returns true for recent`() {
        assertTrue(VirtualPathUtils.isVirtualPath(LocalMediaScanner.VIRTUAL_PATH_RECENT))
    }

    @Test
    fun `isVirtualPath returns true for all_audio`() {
        assertTrue(VirtualPathUtils.isVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO))
    }

    @Test
    fun `isVirtualPath returns true for all_video`() {
        assertTrue(VirtualPathUtils.isVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO))
    }

    @Test
    fun `isVirtualPath returns true for all_docs`() {
        assertTrue(VirtualPathUtils.isVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS))
    }

    @Test
    fun `isVirtualPath returns true for all_images`() {
        assertTrue(VirtualPathUtils.isVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES))
    }

    @Test
    fun `isVirtualPath returns false for real local path`() {
        assertFalse(VirtualPathUtils.isVirtualPath("/storage/emulated/0/DCIM"))
    }

    @Test
    fun `isVirtualPath returns false for empty string`() {
        assertFalse(VirtualPathUtils.isVirtualPath(""))
    }

    @Test
    fun `isVirtualPath returns false for smb path`() {
        assertFalse(VirtualPathUtils.isVirtualPath("smb://server/share"))
    }

    @Test
    fun `isAggregateVirtualPath returns true for all_audio`() {
        assertTrue(VirtualPathUtils.isAggregateVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO))
    }

    @Test
    fun `isAggregateVirtualPath returns true for all_video`() {
        assertTrue(VirtualPathUtils.isAggregateVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO))
    }

    @Test
    fun `isAggregateVirtualPath returns true for all_docs`() {
        assertTrue(VirtualPathUtils.isAggregateVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS))
    }

    @Test
    fun `isAggregateVirtualPath returns true for all_images`() {
        assertTrue(VirtualPathUtils.isAggregateVirtualPath(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES))
    }

    @Test
    fun `isAggregateVirtualPath returns false for recent`() {
        assertFalse(VirtualPathUtils.isAggregateVirtualPath(LocalMediaScanner.VIRTUAL_PATH_RECENT))
    }

    @Test
    fun `isAggregateVirtualPath returns false for real path`() {
        assertFalse(VirtualPathUtils.isAggregateVirtualPath("/storage/emulated/0/Music"))
    }

    @Test
    fun `ALL_VIRTUAL_PATHS contains all six paths`() {
        assertEquals(6, VirtualPathUtils.ALL_VIRTUAL_PATHS.size)
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_RECENT))
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO))
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO))
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES))
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS))
        assertTrue(VirtualPathUtils.ALL_VIRTUAL_PATHS.contains(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS))
    }
}
