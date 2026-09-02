package com.sza.fastmediasorter.wear.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * S2356: the rebuild is the one path that writes note rows without the DAO, so what it writes is
 * only guaranteed by this file.
 *
 * The column-parity test is what ADR-4 trades for that freedom: when S2161 adds its published-
 * address column the suite goes red here, instead of the recovered list quietly dropping the field.
 */
class VoiceNoteIndexRebuilderTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    /**
     * Delegates every member it does not name to a relaxed mock, so a future androidx addition to
     * `SupportSQLiteDatabase` cannot break this file.
     */
    private class RecordingDatabase(
        private val delegate: SupportSQLiteDatabase = mockk(relaxed = true)
    ) : SupportSQLiteDatabase by delegate {

        val statements = mutableListOf<String>()
        val rows = mutableListOf<List<Any?>>()

        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            statements += sql
            rows += bindArgs.toList()
        }
    }

    private fun rebuilderOver(directory: File, durationMillis: Long = 1_000L): VoiceNoteIndexRebuilder {
        val factory = mockk<VoiceNoteFileFactory>()
        every { factory.directory() } returns directory
        return VoiceNoteIndexRebuilder(factory, VoiceNoteDurationReader { durationMillis })
    }

    private fun columnsOf(statement: String): List<String> =
        statement.substringAfter('(').substringBefore(')').split(",").map { it.trim() }

    private fun valueOf(database: RecordingDatabase, row: Int, column: String): Any? {
        val index = columnsOf(database.statements[row]).indexOf(column)
        return database.rows[row][index]
    }

    @Test
    fun `every recording becomes a row in the notes table`() {
        temporaryFolder.newFile("audio_260902_101500.m4a")
        temporaryFolder.newFile("audio_260902_101600.m4a")
        val database = RecordingDatabase()

        val written = rebuilderOver(temporaryFolder.root).rebuildInto(database)

        assertEquals(2, written)
        assertEquals(2, database.statements.size)
        assertTrue(database.statements.all { it.contains("INSERT INTO voice_notes") })
    }

    @Test
    fun `a rebuilt note waits for a manual send`() {
        temporaryFolder.newFile("audio_260902_101500.m4a")
        val database = RecordingDatabase()

        rebuilderOver(temporaryFolder.root).rebuildInto(database)

        assertEquals(
            VoiceNoteDeliveryState.LOCAL_ONLY.name,
            valueOf(database, 0, "deliveryState")
        )
    }

    @Test
    fun `a factory-shaped name supplies the creation time`() {
        val file = temporaryFolder.newFile("audio_260902_101500.m4a")
        file.setLastModified(1L)
        val database = RecordingDatabase()

        rebuilderOver(temporaryFolder.root).rebuildInto(database)

        val expected = SimpleDateFormat("yyMMdd_HHmmss", Locale.US).parse("260902_101500")!!.time
        assertEquals(expected, valueOf(database, 0, "createdAtMillis"))
        assertNotEquals(file.lastModified(), valueOf(database, 0, "createdAtMillis"))
    }

    @Test
    fun `an unparseable name falls back to the file stamp`() {
        val file = temporaryFolder.newFile("keepsake.m4a")
        val stamp = 1_600_000_000_000L
        file.setLastModified(stamp)
        val database = RecordingDatabase()

        rebuilderOver(temporaryFolder.root).rebuildInto(database)

        assertEquals(file.lastModified(), valueOf(database, 0, "createdAtMillis"))
    }

    @Test
    fun `a recording with no readable duration still becomes a note`() {
        temporaryFolder.newFile("audio_260902_101500.m4a")
        val database = RecordingDatabase()

        val written = rebuilderOver(temporaryFolder.root, durationMillis = 0L).rebuildInto(database)

        assertEquals(1, written)
        assertEquals(0L, valueOf(database, 0, "durationMillis"))
    }

    @Test
    fun `an empty directory writes nothing`() {
        val database = RecordingDatabase()

        val written = rebuilderOver(temporaryFolder.root).rebuildInto(database)

        assertEquals(0, written)
        assertTrue(database.statements.isEmpty())
    }

    @Test
    fun `a file that is not a recording is ignored`() {
        temporaryFolder.newFile("notes.txt")
        val database = RecordingDatabase()

        val written = rebuilderOver(temporaryFolder.root).rebuildInto(database)

        assertEquals(0, written)
    }

    @Test
    fun `the written columns still match the entity fields`() {
        temporaryFolder.newFile("audio_260902_101500.m4a")
        val database = RecordingDatabase()

        rebuilderOver(temporaryFolder.root).rebuildInto(database)

        // Instance fields only: the Compose compiler plugin applies to the whole module and adds a
        // static `$stable` to every class, which is not a column and must not be matched by name.
        val declared = VoiceNoteEntity::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .filterNot { it == "id" }
            .toSet()

        assertEquals(
            "VoiceNoteEntity and VoiceNoteIndexRebuilder disagree on the column set - " +
                "update the WRITTEN_COLUMNS list in VoiceNoteIndexRebuilder.kt",
            declared,
            columnsOf(database.statements.single()).toSet()
        )
    }
}
