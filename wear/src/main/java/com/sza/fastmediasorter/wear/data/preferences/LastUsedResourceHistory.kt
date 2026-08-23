package com.sza.fastmediasorter.wear.data.preferences

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
    private const val FIELD_COUNT = 2

    fun encode(entries: List<LastUsedResource>): String =
        entries.joinToString(RECORD_SEPARATOR) { it.id + FIELD_SEPARATOR + it.name }

    /** A record that lost a field addresses nothing, so it is dropped rather than reported. */
    fun decode(stored: String?): List<LastUsedResource> = stored
        ?.split(RECORD_SEPARATOR)
        ?.mapNotNull { record ->
            val fields = record.split(FIELD_SEPARATOR)
            val id = fields.getOrNull(ID_INDEX)
            val name = fields.getOrNull(NAME_INDEX)
            if (fields.size == FIELD_COUNT && !id.isNullOrEmpty() && !name.isNullOrEmpty()) {
                LastUsedResource(id, name)
            } else {
                null
            }
        }
        .orEmpty()

    /**
     * Puts [entry] at the front, dropping any earlier record for the same id: opening a resource
     * twice makes it the most recent one, never a second cell captioned the same way.
     */
    fun push(current: List<LastUsedResource>, entry: LastUsedResource): List<LastUsedResource> =
        (listOf(entry) + current.filterNot { it.id == entry.id })
            .take(LastUsedResource.HISTORY_LIMIT)
}
