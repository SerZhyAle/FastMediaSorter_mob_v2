package com.sza.fastmediasorter.data.local.staging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [LocalStagingRegistry] is a pure in-memory map keyed by [File.absolutePath]; no Android deps.
 */
class LocalStagingRegistryTest {

    private val registry = LocalStagingRegistry()

    private fun file(path: String) = File(path)

    @Test
    fun `register then lookup returns the stored entry`() {
        val f = file("/tmp/note.txt")
        registry.register(
            file = f,
            targetResourceId = 7L,
            targetParentPath = "smb://host/share",
            intendedName = "note.txt",
            kind = StagedKind.TEXT_NOTE,
        )

        val entry = registry.lookup(f)
        requireNotNull(entry)
        assertEquals(f.absolutePath, entry.localFile.absolutePath)
        assertEquals(7L, entry.targetResourceId)
        assertEquals("smb://host/share", entry.targetParentPath)
        assertEquals("note.txt", entry.intendedName)
        assertEquals(StagedKind.TEXT_NOTE, entry.kind)
    }

    @Test
    fun `register defaults location to NETWORK_STAGED`() {
        val f = file("/tmp/a.txt")
        registry.register(f, 1L, "p", "a.txt", StagedKind.DRAWING)
        assertEquals(LocalStagingRegistry.Location.NETWORK_STAGED, registry.lookup(f)?.location)
    }

    @Test
    fun `register honours explicit LOCAL_DEFERRED location`() {
        val f = file("/tmp/b.txt")
        registry.register(f, 1L, "p", "b.txt", StagedKind.DRAWING, LocalStagingRegistry.Location.LOCAL_DEFERRED)
        assertEquals(LocalStagingRegistry.Location.LOCAL_DEFERRED, registry.lookup(f)?.location)
    }

    @Test
    fun `re-registering the same path overwrites the prior entry`() {
        val f = file("/tmp/c.txt")
        registry.register(f, 1L, "old", "c.txt", StagedKind.TEXT_NOTE)
        registry.register(f, 2L, "new", "c.txt", StagedKind.DRAWING)

        val entry = registry.lookup(f)
        assertEquals(2L, entry?.targetResourceId)
        assertEquals("new", entry?.targetParentPath)
        assertEquals(StagedKind.DRAWING, entry?.kind)
    }

    @Test
    fun `unregister removes and returns the entry`() {
        val f = file("/tmp/d.txt")
        registry.register(f, 1L, "p", "d.txt", StagedKind.TEXT_NOTE)

        val removed = registry.unregister(f)
        assertEquals(f.absolutePath, removed?.localFile?.absolutePath)
        assertNull(registry.lookup(f))
    }

    @Test
    fun `lookup and unregister of unknown file return null`() {
        val f = file("/tmp/missing.txt")
        assertNull(registry.lookup(f))
        assertNull(registry.unregister(f))
    }

    @Test
    fun `snapshot reflects all current entries`() {
        registry.register(file("/tmp/e1.txt"), 1L, "p", "e1.txt", StagedKind.TEXT_NOTE)
        registry.register(file("/tmp/e2.txt"), 1L, "p", "e2.txt", StagedKind.DRAWING)

        val snapshot = registry.snapshot()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.any { it.intendedName == "e1.txt" })
        assertTrue(snapshot.any { it.intendedName == "e2.txt" })
    }
}
