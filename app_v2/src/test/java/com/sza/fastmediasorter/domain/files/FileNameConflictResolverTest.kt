package com.sza.fastmediasorter.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileNameConflictResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolveLocal returns original name and false when no collision`() {
        val dir = tempFolder.newFolder("fnc")

        val (name, renamed) = FileNameConflictResolver.resolveLocal(dir, "fresh.txt")

        assertEquals("fresh.txt", name)
        assertFalse(renamed)
    }

    @Test
    fun `resolveLocal appends the contract ordinal on collision`() {
        val dir = tempFolder.newFolder("fnc")
        File(dir, "dup.txt").createNewFile()

        val (name, renamed) = FileNameConflictResolver.resolveLocal(dir, "dup.txt")

        assertEquals("dup (2).txt", name)
        assertTrue(renamed)
    }

    @Test
    fun `resolveLocal skips every ordinal already taken`() {
        val dir = tempFolder.newFolder("fnc")
        File(dir, "dup.txt").createNewFile()
        File(dir, "dup (2).txt").createNewFile()

        val (name, _) = FileNameConflictResolver.resolveLocal(dir, "dup.txt")

        assertEquals("dup (3).txt", name)
    }

    @Test
    fun `resolveLocal splits on the last dot only`() {
        val dir = tempFolder.newFolder("fnc")
        File(dir, "my.note.txt").createNewFile()

        val (name, _) = FileNameConflictResolver.resolveLocal(dir, "my.note.txt")

        assertEquals("my.note (2).txt", name)
    }
}
