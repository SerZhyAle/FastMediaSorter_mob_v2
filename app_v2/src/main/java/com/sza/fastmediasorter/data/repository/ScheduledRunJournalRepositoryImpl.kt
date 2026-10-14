package com.sza.fastmediasorter.data.repository

import android.content.Context
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunRecord
import com.sza.fastmediasorter.domain.repository.ScheduledRunJournalRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-backed [ScheduledRunJournalRepository]: one small tab-separated file per schedule under
 * `filesDir/scheduled_run_journal/`. A file rather than a Room table because the journal is a
 * bounded, append-mostly list that nothing queries, and a table would cost a schema migration.
 */
@Singleton
class ScheduledRunJournalRepositoryImpl(
    private val directory: File,
) : ScheduledRunJournalRepository {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIRECTORY_NAME))

    // One writer at a time: the worker appends while the history dialog may be reading, and a
    // read-modify-write of the same small file must not interleave with another append.
    private val lock = Mutex()

    override suspend fun append(operationId: Long, record: ScheduledRunRecord) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    directory.mkdirs()
                    val lines = readLines(operationId) + encode(record)
                    fileOf(operationId).writeText(lines.takeLast(MAX_RECORDS).joinToString("\n") + "\n")
                } catch (e: IOException) {
                    Timber.w(e, "ScheduledRunJournal: could not append for op=$operationId")
                }
            }
        }
    }

    override suspend fun read(operationId: Long): List<ScheduledRunRecord> = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                readLines(operationId).mapNotNull(::decode).asReversed()
            } catch (e: IOException) {
                Timber.w(e, "ScheduledRunJournal: could not read op=$operationId")
                emptyList()
            }
        }
    }

    override suspend fun clear(operationId: Long) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val file = fileOf(operationId)
                if (file.exists() && !file.delete()) {
                    Timber.w("ScheduledRunJournal: could not delete journal of op=$operationId")
                }
            }
        }
    }

    override suspend fun clearAll() {
        lock.withLock {
            withContext(Dispatchers.IO) {
                directory.listFiles()?.forEach { file ->
                    if (!file.delete()) Timber.w("ScheduledRunJournal: could not delete ${file.name}")
                }
            }
        }
    }

    private fun fileOf(operationId: Long) = File(directory, "$operationId.tsv")

    private fun readLines(operationId: Long): List<String> {
        val file = fileOf(operationId)
        return if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
    }

    private fun encode(record: ScheduledRunRecord): String = listOf(
        record.startedAt.toString(),
        record.finishedAt.toString(),
        record.outcome.name,
        record.filesProcessed.toString(),
        sanitize(record.message),
        sanitize(record.note),
    ).joinToString(SEPARATOR.toString())

    /** A tab or a line break inside a free-text field would shift every later column of its row. */
    private fun sanitize(text: String?): String =
        text.orEmpty().replace(SEPARATOR, ' ').replace('\n', ' ').replace('\r', ' ').trim()

    private fun decode(line: String): ScheduledRunRecord? {
        val parts = line.split(SEPARATOR)
        val outcome = ScheduledRunOutcome.entries.firstOrNull { it.name == parts.getOrNull(OUTCOME_INDEX) }
        // started, finished, files - a row missing or mangling any of them is dropped as a whole.
        val numbers = listOf(STARTED_INDEX, FINISHED_INDEX, FILES_INDEX)
            .mapNotNull { parts.getOrNull(it)?.toLongOrNull() }
        return if (outcome == null || numbers.size != NUMERIC_COLUMNS) {
            null
        } else {
            ScheduledRunRecord(
                startedAt = numbers[0],
                finishedAt = numbers[1],
                outcome = outcome,
                filesProcessed = numbers[2].toInt(),
                message = parts.getOrNull(MESSAGE_INDEX)?.takeIf { it.isNotEmpty() },
                note = parts.getOrNull(NOTE_INDEX)?.takeIf { it.isNotEmpty() },
            )
        }
    }

    companion object {
        const val DIRECTORY_NAME = "scheduled_run_journal"

        /** Runs kept per schedule; older ones are dropped on the next append. */
        const val MAX_RECORDS = 30

        private const val SEPARATOR = '\t'
        private const val STARTED_INDEX = 0
        private const val FINISHED_INDEX = 1
        private const val OUTCOME_INDEX = 2
        private const val FILES_INDEX = 3
        private const val MESSAGE_INDEX = 4
        private const val NOTE_INDEX = 5
        private const val NUMERIC_COLUMNS = 3
    }
}
