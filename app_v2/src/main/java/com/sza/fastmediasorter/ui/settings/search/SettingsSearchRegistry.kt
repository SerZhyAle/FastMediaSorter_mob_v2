package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings-search index, lazily built from the auto-indexing pipeline:
 *   `SettingsSearchSource` → `SettingsSearchKeywordCollector` → `SettingsSearchAvailability` filter.
 *
 * The heavy work (XML scan + per-locale string resolution) runs on the first access to
 * `entries` or `search()` — typically the moment the user opens the search overlay,
 * not on app cold start.
 */
@Singleton
class SettingsSearchRegistry @Inject constructor(
    private val source: SettingsSearchSource,
    private val collector: SettingsSearchKeywordCollector,
    private val availability: SettingsSearchAvailability
) {

    private val allEntries: List<SettingsSearchIndex> by lazy {
        val raw = source.collect()
        val enriched = raw.mapNotNull { collector.enrich(it) }
        Timber.d("S0284: settings search index built (raw=${raw.size}, enriched=${enriched.size})")
        enriched
    }

    val entries: List<SettingsSearchIndex>
        get() = allEntries.filter { availability.isAvailable(it.sectionId) }

    fun search(query: String): List<SettingsSearchIndex> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return entries
        return entries.filter { entry ->
            entry.title.lowercase().contains(normalized) ||
                entry.keywords.any { keyword -> keyword.contains(normalized) }
        }
    }
}
