package com.sza.fastmediasorter.domain.usecase.notes

import android.os.Build
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.notes.NotesSyncState
import com.sza.fastmediasorter.data.notes.NotesSyncStateStore
import com.sza.fastmediasorter.domain.exchange.NotesSyncRun
import com.sza.fastmediasorter.domain.model.notes.NotesSyncOutcome
import com.sza.fastmediasorter.domain.repository.NotesSyncRemote
import com.sza.fastmediasorter.util.NotesFolderPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-way sync of the Notes folder with the account's notes store (S4572). Pulls the records changed since the last
 * `notesRevision`, then pushes every note file changed since the last sync and every delete. Item FJ's conflict
 * rules mean no sync ever loses text: the losing local version is always kept as a note of its own.
 */
@Singleton
class SyncNotesThroughExchangeUseCase @Inject constructor(
    private val remote: NotesSyncRemote,
    private val store: NotesSyncStateStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Two syncs over the same folder would both see a file as changed and send it twice.
    private val mutex = Mutex()

    suspend operator fun invoke(
        folder: File = NotesFolderPolicy.notesDirectory(),
        deviceName: String = Build.MODEL.orEmpty(),
    ): NotesSyncOutcome = mutex.withLock {
        Timber.d("S4572: notes sync run started")
        val entry = remote.entry() ?: return@withLock NotesSyncOutcome.Off
        val stored = store.read()
        // Revisions of one server mean nothing to another, so a new entry starts with every note new.
        val initial = if (stored.entryIdentity == entry.identity) {
            stored
        } else {
            NotesSyncState.EMPTY.copy(entryIdentity = entry.identity)
        }
        withContext(ioDispatcher) {
            NotesSyncRun(remote, entry, folder, deviceName, initial, store::write).run()
        }
    }
}
