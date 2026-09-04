package com.sza.fastmediasorter.wear.ui.voicenote

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.util.MediaMimeTypes
import java.io.File

/**
 * S2495: a voice note seen as an ordinary media file, so the module's shared file surfaces can take
 * it - the actions dialog, the capability policy and the operation executor all speak in
 * [WearMediaFile] and know nothing about notes.
 *
 * ## Which of the note's two halves is its address
 *
 * A published note exists twice: as the private file the note index was built from, and as a copy in
 * the shared audio collection. The private file is the address handed on, and the published row is
 * carried alongside in [publishedUri] rather than substituted for it. Three reasons, in order of
 * weight:
 *
 * - The index knows the note by its private file. Handing the published row as the identity would
 *   make every operation act on the copy while the list kept showing the original.
 * - The private file classifies as app-owned, so the owner's own recording is renamed and deleted
 *   without a system write confirmation. The published row classifies as MediaStore, which asks for
 *   one on every operation - a confirmation for touching a file the app itself wrote.
 * - Deleting through the note repository already removes both the row and the file. Routing delete
 *   through the shared executor against the published address would remove one half only, which is
 *   the split strategic §7 rates as the ticket's highest risk.
 *
 * The tactical plan asked for the published address to be preferred; measuring the three points above
 * against the code reversed it, and phase 04's step 04.1 records the reversal. What the plan actually
 * needed - that both halves are known at the point an operation runs - is what [publishedUri] carries.
 */
internal data class VoiceNoteMediaFile(
    /** The note as the shared surfaces see it, addressed by its private file. */
    val file: WearMediaFile,
    /** The published row, or null while the note has never reached the shared collection. */
    val publishedUri: Uri?
) {
    /** True when an operation has to keep a second half in step; false when there is only one. */
    val isPublished: Boolean get() = publishedUri != null
}

/**
 * Maps [note] onto the media-file model.
 *
 * [dateModified] carries the recording time rather than the file's own clock: the note list orders by
 * it, and a note whose bytes were rewritten by a transfer would otherwise sort as if freshly recorded.
 */
/** What a two-sided rename ended up doing, which is more than "it worked" or "it did not". */
internal enum class VoiceNoteRenameOutcome {
    /** Both halves carry the new name. */
    SUCCEEDED,

    /** The shared collection refused before anything moved, so neither half changed. */
    REFUSED_BEFORE_ANY_MOVE,

    /** The row moved, the private file did not, and the row was put back under its old name. */
    ROLLED_BACK
}

/**
 * S2495: renames both halves of [note], or leaves both as they were.
 *
 * The published row is asked first because it is the half that can refuse - the shared collection may
 * answer that the write needs the owner's confirmation - and a refusal before anything has moved costs
 * nothing to recover from. Only once it has agreed does [renamePrivate] move the private file and the
 * index together; if that fails, the row is put back, because a note visible under two names is the
 * failure strategic §7 rates first.
 *
 * The two collaborators arrive as functions rather than as objects so this decision can be exercised
 * without a `Context`: the ViewModel that calls it reads a preferences notice in its `init` block,
 * which on the plain JVM would need an instrumentation runner the wear unit source set does not carry.
 */
internal suspend fun renameVoiceNote(
    note: VoiceNote,
    newName: String,
    renamePublished: (Uri, String) -> Boolean,
    renamePrivate: suspend (String) -> Boolean
): VoiceNoteRenameOutcome {
    val published = note.toMediaFile().publishedUri
    if (published != null && !renamePublished(published, newName)) {
        return VoiceNoteRenameOutcome.REFUSED_BEFORE_ANY_MOVE
    }
    return if (renamePrivate(newName)) {
        VoiceNoteRenameOutcome.SUCCEEDED
    } else {
        published?.let { renamePublished(it, note.fileName) }
        VoiceNoteRenameOutcome.ROLLED_BACK
    }
}

internal fun VoiceNote.toMediaFile(): VoiceNoteMediaFile = VoiceNoteMediaFile(
    file = WearMediaFile(
        id = id,
        name = fileName,
        uri = Uri.fromFile(File(absolutePath)),
        mimeType = MediaMimeTypes.fromFileName(fileName),
        size = sizeBytes,
        dateModified = createdAtMillis,
        duration = durationMillis
    ),
    publishedUri = publishedAddress?.takeIf { it.isNotBlank() }?.let(Uri::parse)
)
