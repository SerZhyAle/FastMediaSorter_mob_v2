package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A song tapped in a virtual collection must reload the collection, never the physical folder the
 * song happens to sit in: that folder may refuse direct enumeration while MediaStore serves the song.
 */
class PlayerVirtualResourceLaunchTest {

    @Test
    fun `virtual collection launched at a MediaStore-only song keeps the whole collection`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            initialFilePath = "/storage/emulated/0/Music/ofenbach-rock-it.mp3",
            currentFilePath = null,
        )

        assertNull(scope)
    }

    @Test
    fun `virtual collection reload during playback keeps the whole collection`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            initialFilePath = null,
            currentFilePath = "/storage/emulated/0/Download/clip.mp4",
        )

        assertNull(scope)
    }

    @Test
    fun `virtual collection launched at a missing song still scans the collection`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            initialFilePath = "/storage/emulated/0/Music/removed-song.mp3",
            currentFilePath = "/storage/emulated/0/Music/other.mp3",
        )

        assertNull(scope)
    }

    @Test
    fun `physical resource launched at a file in a subfolder lists that subfolder`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = "/storage/emulated/0/Music",
            initialFilePath = "/storage/emulated/0/Music/Albums/track01.mp3",
            currentFilePath = null,
        )

        assertEquals("/storage/emulated/0/Music/Albums", scope)
    }

    @Test
    fun `physical resource without a launch file falls back to the playing file folder`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = "smb://host/share",
            initialFilePath = null,
            currentFilePath = "smb://host/share/sub/track02.mp3",
        )

        assertEquals("smb://host/share/sub", scope)
    }

    @Test
    fun `physical resource without any file path lists the resource root`() {
        val scope = resolvePlayerFolderScope(
            resourcePath = "/storage/emulated/0/Music",
            initialFilePath = null,
            currentFilePath = null,
        )

        assertNull(scope)
    }
}
