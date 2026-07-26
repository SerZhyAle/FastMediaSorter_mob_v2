package com.sza.fastmediasorter.data.local.db

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1159: `resources_fts` is an external-content FTS4 table whose index is maintained by the
 * Room-generated content-sync triggers, not by [ResourceDao]. These tests assert the index
 * invariants directly (via `MATCH` against the virtual table, bypassing the JOIN in
 * [ResourceDao.searchResourcesFts] that would hide orphan entries) so that re-adding hand-rolled
 * FTS statements - or losing the triggers - fails here instead of silently corrupting search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResourceFtsSyncTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.resourceDao()

    private fun resource(name: String, path: String) = ResourceEntity(
        name = name,
        path = path,
        type = ResourceType.LOCAL,
    )

    /** Raw docids carrying [term] in the full-text index, duplicates included. */
    private fun indexedDocIds(term: String): List<Long> {
        val docIds = mutableListOf<Long>()
        dbRule.db.openHelper.writableDatabase
            .query("SELECT docid FROM resources_fts WHERE resources_fts MATCH '$term'")
            .use { cursor -> while (cursor.moveToNext()) docIds.add(cursor.getLong(0)) }
        return docIds
    }

    @Test
    fun `content sync triggers are installed for the fts table`() {
        val triggers = mutableListOf<String>()
        dbRule.db.openHelper.writableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'trigger'")
            .use { cursor -> while (cursor.moveToNext()) triggers.add(cursor.getString(0)) }

        assertEquals(
            "Room must own the resources_fts index via content-sync triggers",
            listOf(
                "room_fts_content_sync_resources_fts_AFTER_INSERT",
                "room_fts_content_sync_resources_fts_AFTER_UPDATE",
                "room_fts_content_sync_resources_fts_BEFORE_DELETE",
                "room_fts_content_sync_resources_fts_BEFORE_UPDATE",
            ),
            triggers.sorted()
        )
    }

    @Test
    fun `insert indexes the resource exactly once`() = runTest {
        val id = dao.insert(resource("Holiday", "/dcim/holiday"))

        assertEquals(listOf(id), indexedDocIds("Holiday"))
        assertEquals(listOf(id), dao.searchResourcesFts("Holiday").map { it.id })
    }

    @Test
    fun `deleteById drops the index entry`() = runTest {
        val id = dao.insert(resource("Doomed", "/x/doomed"))

        dao.deleteById(id)

        assertTrue(indexedDocIds("Doomed").isEmpty())
        assertTrue(dao.searchResourcesFts("Doomed").isEmpty())
    }

    @Test
    fun `delete by entity drops the index entry`() = runTest {
        val id = dao.insert(resource("Condemned", "/x/condemned"))

        dao.delete(dao.getResourceByIdSync(id)!!)

        assertTrue(indexedDocIds("Condemned").isEmpty())
    }

    @Test
    fun `rename replaces the indexed term`() = runTest {
        val id = dao.insert(resource("OldName", "/x/old"))

        dao.update(dao.getResourceByIdSync(id)!!.copy(name = "BrandNew"))

        assertTrue(indexedDocIds("OldName").isEmpty())
        assertEquals(listOf(id), indexedDocIds("BrandNew"))
    }

    @Test
    fun `re-inserting an existing id leaves no stale term`() = runTest {
        val id = dao.insert(resource("ConflictOld", "/x/conflict"))

        dao.insert(dao.getResourceByIdSync(id)!!.copy(name = "ConflictNew"))

        assertTrue(indexedDocIds("ConflictOld").isEmpty())
        assertEquals(listOf(id), indexedDocIds("ConflictNew"))
    }

    @Test
    fun `deleteAll empties the index`() = runTest {
        dao.insert(resource("Alpha", "/a"))
        dao.insert(resource("Beta", "/b"))

        dao.deleteAll()

        assertTrue(indexedDocIds("Alpha").isEmpty())
        assertTrue(indexedDocIds("Beta").isEmpty())
    }
}
