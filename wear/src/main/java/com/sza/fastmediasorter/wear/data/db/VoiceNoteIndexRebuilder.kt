package com.sza.fastmediasorter.wear.data.db

import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sza.fastmediasorter.wear.data.recorder.VoiceNoteFileFactory
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteDeliveryState
import timber.log.Timber
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors `VoiceNoteFileFactory`: the only extension the watch ever writes. */
private const val VOICE_NOTE_EXTENSION = "m4a"

/** Mirrors `VoiceNoteFileFactory`'s pattern - the same stamp, read back out of the name. */
private const val DATE_TIME_PATTERN = "yyMMdd_HHmmss"

/** `audio_yyMMdd_HHmmss` plus the optional `_<ordinal>` collision suffix the factory appends. */
private val FILE_NAME_TIMESTAMP = Regex("""^audio_(\d{6}_\d{6})(?:_\d+)?$""")

private const val TABLE_NAME = "voice_notes"

// ADR-4: the insert cannot go through the DAO, so the column set is spelled out once here and held
// equal to VoiceNoteEntity's fields by VoiceNoteIndexRebuilderTest - when S2161 adds a column that
// test goes red instead of the recovered list silently losing the field.
private const val COLUMN_FILE_NAME = "fileName"
private const val COLUMN_ABSOLUTE_PATH = "absolutePath"
private const val COLUMN_CREATED_AT_MILLIS = "createdAtMillis"
private const val COLUMN_DURATION_MILLIS = "durationMillis"
private const val COLUMN_SIZE_BYTES = "sizeBytes"
private const val COLUMN_DELIVERY_STATE = "deliveryState"

private val WRITTEN_COLUMNS = listOf(
    COLUMN_FILE_NAME,
    COLUMN_ABSOLUTE_PATH,
    COLUMN_CREATED_AT_MILLIS,
    COLUMN_DURATION_MILLIS,
    COLUMN_SIZE_BYTES,
    COLUMN_DELIVERY_STATE
)

private val INSERT_STATEMENT =
    "INSERT INTO $TABLE_NAME (${WRITTEN_COLUMNS.joinToString(", ")}) " +
        "VALUES (${WRITTEN_COLUMNS.joinToString(", ") { "?" }})"

/**
 * S2356: turns the recordings directory back into note rows after the database had to be recreated.
 *
 * Writes through [SupportSQLiteDatabase.execSQL] rather than the DAO because ADR-4 requires the
 * rebuild to finish before the provider returns, and Room refuses a non-`suspend` DAO call on the
 * main thread - which strategic 3.2 shows the provider reaches through service field injection.
 */
@Singleton
class VoiceNoteIndexRebuilder @Inject constructor(
    private val fileFactory: VoiceNoteFileFactory,
    private val durationReader: VoiceNoteDurationReader
) {

    /** Returns how many rows were written. The database must already be open and empty. */
    fun rebuildInto(database: SupportSQLiteDatabase): Int {
        val files = recordingFiles()
        var written = 0
        for (file in files) {
            if (insert(database, file)) {
                written++
            }
        }
        Timber.i("Rebuilt the voice-note index: %d of %d recording(s) written", written, files.size)
        Timber.d("S2356: index rebuild wrote %d row(s) from %d file(s) on disk", written, files.size)
        return written
    }

    /** Oldest first, so the regenerated ids run in the same direction the originals did. */
    private fun recordingFiles(): List<File> {
        val listed = fileFactory.directory().listFiles() ?: return emptyList()
        return listed
            .filter { it.isFile && it.extension.equals(VOICE_NOTE_EXTENSION, ignoreCase = true) }
            .sortedBy { createdAtMillisOf(it) }
    }

    // One unreadable row must not cost the rest of the list: this runs after a failure that already
    // lost the index once, so a partial recovery beats an aborted one.
    private fun insert(database: SupportSQLiteDatabase, file: File): Boolean = try {
        database.execSQL(INSERT_STATEMENT, bindArgsOf(file))
        true
    } catch (e: SQLException) {
        Timber.w(e, "Voice note %s could not be re-indexed", file.name)
        false
    }

    /** [VoiceNoteEntity.id] is deliberately absent - the recreated table generates it. */
    private fun bindArgsOf(file: File): Array<Any> = arrayOf(
        file.name,
        file.absolutePath,
        createdAtMillisOf(file),
        durationReader.durationMillisOf(file),
        file.length(),
        VoiceNoteDeliveryState.LOCAL_ONLY.name
    )

    /**
     * The creation time is read out of the name the factory wrote, which survives a copy that
     * `lastModified` does not; the file's own stamp is the fallback for a name nobody recognises.
     */
    private fun createdAtMillisOf(file: File): Long {
        val stamp = FILE_NAME_TIMESTAMP.find(file.nameWithoutExtension)?.groupValues?.get(1)
            ?: return file.lastModified()
        return try {
            SimpleDateFormat(DATE_TIME_PATTERN, Locale.US).parse(stamp)?.time ?: file.lastModified()
        } catch (e: ParseException) {
            Timber.w(e, "Voice note %s carries an unparseable timestamp", file.name)
            file.lastModified()
        }
    }
}
