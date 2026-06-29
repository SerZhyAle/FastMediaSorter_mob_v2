package com.sza.fastmediasorter.domain.model

/**
 * S0659: persisted default ordering for the "Трансляции" list. Seeds the list screen on first open /
 * after a cleared session. Maps onto the UI-side `StreamsViewModel.SortMode` in the ViewModel; this
 * domain enum must stay UI-agnostic so the settings/persistence layer never depends on a ViewModel.
 */
enum class StreamDefaultSort {
    NAME,
    TOPIC,
    LANGUAGE,
    COUNTRY,
    RECENT;

    companion object {
        val DEFAULT: StreamDefaultSort = NAME

        fun fromName(name: String?): StreamDefaultSort =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
