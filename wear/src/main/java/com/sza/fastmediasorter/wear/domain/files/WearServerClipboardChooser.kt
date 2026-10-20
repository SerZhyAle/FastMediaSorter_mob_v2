package com.sza.fastmediasorter.wear.domain.files

import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue

/**
 * S4227: which entry's value "get clipboard from server" takes (contract DEVICE-EXCHANGE 22 item CA): the
 * latest `storedAt`, on equal times the entry listed first on the watch. The person is asked nothing.
 */
object WearServerClipboardChooser {

    private val LATEST_FIRST: Comparator<IndexedValue<WearServerClipboardValue>> =
        compareBy<IndexedValue<WearServerClipboardValue>> { it.value.storedAtMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.index }

    /**
     * [answers] is in the watch's entry order; a null is an empty clipboard or an entry that did not answer.
     * Returns the index of the chosen value, or null when there is none. A `storedAt` this build cannot parse
     * ranks below every parsed one.
     */
    fun choose(answers: List<WearServerClipboardValue?>): Int? = answers
        .withIndex()
        .mapNotNull { (index, value) -> value?.let { IndexedValue(index, it) } }
        .maxWithOrNull(LATEST_FIRST)
        ?.index
}
