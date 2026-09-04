package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource

/**
 * S1974: the joined form of the home screen's last-used history and the rules that reorder it.
 *
 * A separate object rather than private helpers on the repository: none of these five cases - a
 * repeat opening, truncation, a legacy entry, a malformed record - is reachable on a watch without
 * editing DataStore by hand, so they are pinned by a test that must not need DataStore to run.
 *
 * The history is one string by the same reasoning as the calculator's: a preferences string set has
 * no order, and this history is defined newest first.
 */
internal object LastUsedResourceHistory {

    /** Control characters, so no source name or identifier can contain either separator. */
    private const val RECORD_SEPARATOR = "\u001E"
    private const val FIELD_SEPARATOR = "\u001F"

    private const val ID_INDEX = 0
    private const val NAME_INDEX = 1
    private const val KIND_INDEX = 2

    /** S2499: what a record written before the kind field had, and what one written now has. */
    private const val LEGACY_FIELD_COUNT = 2
    private const val FIELD_COUNT = 3

    fun encode(entries: List<LastUsedResource>): String =
        entries.joinToString(RECORD_SEPARATOR) {
            it.id + FIELD_SEPARATOR + it.name + FIELD_SEPARATOR + it.kind.name
        }

    /**
     * A record that lost a field addresses nothing, so it is dropped rather than reported.
     *
     * S2499: a record of [LEGACY_FIELD_COUNT] fields predates the kind and is a resource, which is
     * what keeps an upgraded install's shortcuts. A kind that this build does not know is dropped
     * like any other unreadable value - it addresses a store that is not here.
     */
    fun decode(stored: String?): List<LastUsedResource> = stored
        ?.split(RECORD_SEPARATOR)
        ?.mapNotNull(::decodeRecord)
        .orEmpty()

    private fun decodeRecord(record: String): LastUsedResource? {
        val fields = record.split(FIELD_SEPARATOR)
        val id = fields.getOrNull(ID_INDEX).orEmpty()
        val name = fields.getOrNull(NAME_INDEX).orEmpty()
        val kind = kindOf(fields)
        return if (id.isNotEmpty() && name.isNotEmpty() && kind != null) {
            LastUsedResource(id, name, kind)
        } else {
            null
        }
    }

    /** Null is "this record is not readable by this build", which is the same answer as a lost field. */
    private fun kindOf(fields: List<String>): LastUsedKind? = when (fields.size) {
        LEGACY_FIELD_COUNT -> LastUsedKind.RESOURCE
        FIELD_COUNT -> LastUsedKind.entries.firstOrNull { it.name == fields[KIND_INDEX] }
        else -> null
    }

    /**
     * Puts [entry] at the front, dropping any earlier record for the same target: opening a resource
     * twice makes it the most recent one, never a second cell captioned the same way.
     *
     * S2499: the target is the kind and the id together, because `id` means a source id under one
     * kind and a channel address under the other - matching on it alone would let one evict the
     * other on a coincidence of spelling.
     */
    fun push(current: List<LastUsedResource>, entry: LastUsedResource): List<LastUsedResource> =
        (listOf(entry) + current.filterNot { it.kind == entry.kind && it.id == entry.id })
            .take(LastUsedResource.HISTORY_LIMIT)
}
