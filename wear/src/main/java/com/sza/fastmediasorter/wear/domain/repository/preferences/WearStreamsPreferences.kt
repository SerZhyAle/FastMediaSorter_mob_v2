package com.sza.fastmediasorter.wear.domain.repository.preferences

import kotlinx.coroutines.flow.Flow

/**
 * S2146: how the streams list was last narrowed and ordered, so the choice survives leaving the
 * screen, restarting the app and rebooting the watch.
 *
 * Carried as raw names, unlike the browse pair in [WearBrowsePreferences], and the asymmetry is a
 * layer boundary rather than an oversight: `BrowseSortOrder` and `WearContentType` are domain types,
 * but `StreamSortOrder` and `StreamFilterKind` live in `ui.streams`, and importing a UI enum here
 * would make the domain layer depend on a screen. The screen parses them back, which is also the
 * right place for the fallback when a stored name a later build no longer knows turns up.
 *
 * The keys are named for this one screen, per strategic ADR-6 - a shared "current filter" key
 * would mean narrowing one list silently narrowed another, which the owner did not ask for.
 *
 * The search query is deliberately absent, the same call S2199 made: a restored query empties the
 * list on a word the wearer cannot see.
 */
interface WearStreamsPreferences {

    val streamsSortOrderName: Flow<String?>
    suspend fun setStreamsSortOrderName(name: String?)

    val streamsFilterKindName: Flow<String?>
    suspend fun setStreamsFilterKindName(name: String?)

    val streamsSelectedTopic: Flow<String?>
    suspend fun setStreamsSelectedTopic(topic: String?)

    val streamsSelectedLanguage: Flow<String?>
    suspend fun setStreamsSelectedLanguage(language: String?)
}
