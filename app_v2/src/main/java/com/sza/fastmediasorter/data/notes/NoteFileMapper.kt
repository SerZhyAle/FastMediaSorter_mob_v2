package com.sza.fastmediasorter.data.notes

import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import java.security.MessageDigest
import java.util.UUID

/**
 * Maps a Notes `.txt` file to and from the item FH payload (S4572 strategic 5.1 "Mapping"). A `.txt` holds no
 * title, kind or item id, so [SyncedNote] carries them across: the title is the first line followed by a blank
 * line, a checklist is one `[ ] ` / `[x] ` line per item, and either shape falls back to a text note as soon as
 * the file no longer has that form - never sending a change that dropped what the phone could not show.
 */
object NoteFileMapper {

    private const val UNCHECKED = "[ ] "
    private const val CHECKED = "[x] "
    private const val LF = "\n"

    fun toFileText(payload: NotePayload): String {
        val body = when (payload.kind) {
            NoteKind.TEXT -> NotePayloadCodec.normaliseLineEnds(payload.text.orEmpty())
            NoteKind.CHECKLIST -> payload.items.sortedBy(NoteItem::position)
                .joinToString(LF) { (if (it.checked) CHECKED else UNCHECKED) + it.text }
        }
        return payload.title?.let { "$it$LF$LF$body" } ?: body
    }

    /**
     * The payload of a file whose text is [text]. [previous] is what the last sync left for the file, null for a
     * file never synced; its `createdAt` wins over [createdAt], since item FH's `createdAt` never changes.
     */
    fun fromFileText(text: String, previous: SyncedNote?, createdAt: String, updatedAt: String): NotePayload {
        val lines = NotePayloadCodec.normaliseLineEnds(text).split(LF)
        val title = previous?.title?.takeIf { lines.size >= 2 && lines[0] == it && lines[1].isEmpty() }
        val bodyLines = if (title == null) lines else lines.drop(2)
        val checklist = previous?.kind == NoteKind.CHECKLIST &&
            bodyLines.filter(String::isNotEmpty).all { it.startsWith(UNCHECKED) || it.startsWith(CHECKED) }
        return NotePayload(
            title = title,
            kind = if (checklist) NoteKind.CHECKLIST else NoteKind.TEXT,
            text = if (checklist) null else bodyLines.joinToString(LF),
            items = if (checklist) itemsOf(bodyLines, previous?.itemIds.orEmpty()) else emptyList(),
            createdAt = previous?.createdAt ?: createdAt,
            updatedAt = updatedAt,
        )
    }

    /** The one "changed since the last sync" test (item FJ): hex SHA-256 of the UTF-8 bytes after LF normalising. */
    fun sha256Of(text: String): String {
        val bytes = NotePayloadCodec.normaliseLineEnds(text).toByteArray(Charsets.UTF_8)
        return E2eBytes.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    // Ids are matched by position, so an edited line keeps its id and an added line gets a fresh one.
    private fun itemsOf(lines: List<String>, previousIds: List<String>): List<NoteItem> =
        lines.filter(String::isNotEmpty).mapIndexed { position, line ->
            NoteItem(
                itemId = previousIds.getOrNull(position) ?: UUID.randomUUID().toString(),
                text = line.substring(CHECKED.length),
                checked = line.startsWith(CHECKED),
                position = position,
            )
        }
}
