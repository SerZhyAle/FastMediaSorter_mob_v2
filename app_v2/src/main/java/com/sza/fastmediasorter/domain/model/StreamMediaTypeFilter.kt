package com.sza.fastmediasorter.domain.model

/**
 * S0659: persisted default media-type facet for the "Трансляции" list. Seeds the list screen on first
 * open / after a cleared session. Maps onto the UI-side `StreamsViewModel.MediaKindFilter` in the
 * ViewModel; kept UI-agnostic so the settings/persistence layer never depends on a ViewModel.
 */
enum class StreamMediaTypeFilter {
    ALL,
    AUDIO,
    VIDEO;

    companion object {
        val DEFAULT: StreamMediaTypeFilter = ALL

        fun fromName(name: String?): StreamMediaTypeFilter =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
