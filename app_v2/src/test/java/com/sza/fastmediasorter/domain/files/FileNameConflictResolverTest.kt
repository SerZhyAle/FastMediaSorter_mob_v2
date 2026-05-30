package com.sza.fastmediasorter.domain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

class FileNameConflictResolverTest {

    // 2021-01-01T00:00:42Z → seconds component = "42"
    private val fixedNow = 1_609_459_242_000L
    private val utc = ZoneId.of("UTC")

    @Test
    fun `applySecondsSuffix inserts suffix before extension`() {
        val result = FileNameConflictResolver.applySecondsSuffix("note.txt", fixedNow, utc)
        assertEquals("note-42.txt", result)
    }

    @Test
    fun `applySecondsSuffix appends suffix when no extension`() {
        val result = FileNameConflictResolver.applySecondsSuffix("note", fixedNow, utc)
        assertEquals("note-42", result)
    }

    @Test
    fun `applySecondsSuffix splits on last dot only`() {
        val result = FileNameConflictResolver.applySecondsSuffix("my.note.txt", fixedNow, utc)
        assertEquals("my.note-42.txt", result)
    }

    @Test
    fun `applySecondsSuffix treats leading dot as no stem`() {
        // dotIndex == 0 → else branch, suffix appended to whole name
        val result = FileNameConflictResolver.applySecondsSuffix(".hidden", fixedNow, utc)
        assertEquals(".hidden-42", result)
    }

    @Test
    fun `resolveLocal returns original name and false when no collision`() {
        val dir = Files.createTempDirectory("fnc").toFile()
        try {
            val (name, renamed) = FileNameConflictResolver.resolveLocal(dir, "fresh.txt")
            assertEquals("fresh.txt", name)
            assertFalse(renamed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `resolveLocal returns suffixed name and true when collision exists`() {
        val dir = Files.createTempDirectory("fnc").toFile()
        try {
            File(dir, "dup.txt").also { it.createNewFile() }
            val (name, renamed) = FileNameConflictResolver.resolveLocal(dir, "dup.txt")
            assertTrue(renamed)
            assertTrue("expected suffix on $name", name.matches(Regex("dup-\\d{2}\\.txt")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
