package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalFileProbeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `stat reports length and modification time of a present file`() = runTest {
        val file = tempFolder.newFile("a.png").apply { writeBytes(ByteArray(7)) }

        val stat = LocalFileProbe.stat(file, StandardTestDispatcher(testScheduler))

        assertEquals(7L, stat?.length)
        assertEquals(file.lastModified(), stat?.lastModified)
    }

    @Test
    fun `stat of a missing file is null`() = runTest {
        val missing = File(tempFolder.root, "gone.png")

        assertNull(LocalFileProbe.stat(missing, StandardTestDispatcher(testScheduler)))
    }

    @Test
    fun `protocol paths are returned unchanged`() {
        listOf("content://media/1", "smb://host/share/a.jpg", "sftp://h/a", "ftp://h/a").forEach { path ->
            assertEquals(path, LocalFileProbe.canonicalOrSelf(path))
        }
    }

    @Test
    fun `dot segments resolve so two spellings of one file compare equal`() = runTest {
        val dir = tempFolder.newFolder("dir")
        val file = File(dir, "x.jpg").apply { writeText("x") }
        val dotted = "${tempFolder.root.path}/dir/../dir/./x.jpg"

        val map = LocalFileProbe.canonicalPaths(listOf(dotted, file.path), StandardTestDispatcher(testScheduler))

        assertEquals(map.getValue(file.path), map.getValue(dotted))
    }
}
