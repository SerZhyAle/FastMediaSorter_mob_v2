package com.sza.fastmediasorter.wear.domain.usecase

import android.net.Uri
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.data.preferences.VoiceNoteTitleLanguageStore
import com.sza.fastmediasorter.wear.data.recorder.VoiceNotePublisher
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2626: brings the published voice-note titles onto the watch's current UI language.
 *
 * A title is stored, not rendered: [VoiceNotePublisher] writes it into the shared audio row so every
 * player - ours and any other on the device - reads the note as a date rather than as
 * `audio_260903_155943`. That makes the language of the row the language of the moment it was
 * published, and a watch whose language changed later ends up with a collection in two languages.
 *
 * Rewriting on change rather than formatting on read is the strategic §3.1 decision: the column is
 * read by players that know nothing about this app, and the read side here cannot tell a voice note
 * from any other audio row without consulting the note index for every row it draws.
 *
 * Singleton with a [Mutex] for [DrainPendingVoiceNotesUseCase]'s reason: both entry points can fire
 * close together on a watch that was restarted right after the phone pushed a new language, and two
 * concurrent walks would write every row twice.
 */
@Singleton
class RefreshVoiceNoteTitlesUseCase(
    private val noteRepository: VoiceNoteRepository,
    private val readTag: suspend () -> String?,
    private val writeTag: suspend (tag: String) -> Unit,
    private val retitle: (address: String, title: String) -> Boolean
) {

    /**
     * The collaborators arrive as functions so the walk can be exercised on the plain JVM: parsing an
     * address and reaching a DataStore both need the Android framework, which the wear unit source set
     * has no runner for. Same reason [VoiceNotePublisher] takes its resolver as a parameter.
     */
    @Inject
    constructor(
        noteRepository: VoiceNoteRepository,
        languageStore: VoiceNoteTitleLanguageStore,
        writer: WearMediaStoreFileWriter
    ) : this(
        noteRepository = noteRepository,
        readTag = { languageStore.tag.first() },
        writeTag = { tag -> languageStore.setTag(tag) },
        retitle = { address, title ->
            writer.retitle(Uri.parse(address), title) == WearMediaStoreFileWriter.Result.Succeeded
        }
    )

    private val refreshMutex = Mutex()

    /**
     * Rewrites every published note's title under [activeTag], or does nothing when the stored rows
     * already carry that language.
     */
    suspend operator fun invoke(activeTag: String) {
        refreshMutex.withLock {
            val storedTag = readTag()
            Timber.d("S2626: title refresh asked for $activeTag, rows carry $storedTag")
            if (storedTag == activeTag) return@withLock
            refreshAll(activeTag)
        }
    }

    /**
     * Off the main thread because the rewrite is a blocking binder call per row, and one of the two
     * entry points is the application's start-up coroutine, which runs on `Main.immediate`.
     */
    private suspend fun refreshAll(activeTag: String) = withContext(Dispatchers.IO) {
        var written = 0
        var refused = 0
        for (note in noteRepository.observeNotes().first()) {
            val address = note.publishedAddress?.takeIf { it.isNotBlank() } ?: continue
            if (retitle(address, VoiceNotePublisher.readableTitle(note.createdAtMillis))) {
                written++
            } else {
                refused++
            }
        }
        if (refused > 0) {
            // The marker stays behind so the next start walks again. Recording the language now
            // would leave the refused rows in the old one with nothing left to trigger a retry.
            Timber.w("Voice-note titles: %d row(s) refused the rewrite to %s, retrying next start", refused, activeTag)
            return@withContext
        }
        writeTag(activeTag)
        Timber.i("Voice-note titles rewritten to %s for %d row(s)", activeTag, written)
    }
}
