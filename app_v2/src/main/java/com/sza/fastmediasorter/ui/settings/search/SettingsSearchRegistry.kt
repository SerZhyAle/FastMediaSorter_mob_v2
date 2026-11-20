package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings-search index, lazily built from the auto-indexing pipeline:
 *   `SettingsSearchSource` → `SettingsSearchKeywordCollector` → `SettingsSearchAvailability` +
 *   `SettingsSearchCapabilityGate` (flavor/DI axis) + `SettingsSearchDeviceFeatureGate`
 *   (device/OS axis) filters.
 *
 * The heavy work (XML scan + per-locale string resolution) runs on the first access to
 * `entries` or `search()` - typically the moment the user opens the search overlay,
 * not on app cold start. The XML scan is kept for the process life; the enriched titles are
 * rebuilt when the app language changes, which applies live without a process restart.
 */
@Singleton
class SettingsSearchRegistry @Inject constructor(
    private val source: SettingsSearchSource,
    private val collector: SettingsSearchKeywordCollector,
    private val availability: SettingsSearchAvailability,
    private val capabilityGate: SettingsSearchCapabilityGate,
    private val deviceFeatureGate: SettingsSearchDeviceFeatureGate,
    private val mediaCapabilities: MediaCapabilities
) {

    private val rawEntries: List<RawSettingsSearchEntry> by lazy { source.collect() }

    private val enrichLock = Any()

    // Enriched entries paired with the display language their titles were resolved in.
    @Volatile
    private var enriched: Pair<String, List<SettingsSearchIndex>>? = null

    private fun allEntries(): List<SettingsSearchIndex> {
        val language = collector.displayLanguage()
        val hit = enriched
        if (hit != null && hit.first == language) return hit.second
        return synchronized(enrichLock) {
            val again = enriched
            if (again != null && again.first == language) {
                again.second
            } else {
                Timber.d("S4787: settings search index rebuilt for language=$language")
                rawEntries.mapNotNull { collector.enrich(it, language) }.also { enriched = language to it }
            }
        }
    }

    val entries: List<SettingsSearchIndex>
        get() = allEntries().filter { entry ->
            availability.isAvailable(entry) &&
                capabilityGate.isAvailable(entry) &&
                deviceFeatureGate.isAvailable(entry.key) &&
                isCapabilityAvailable(entry.key)
        }

    private fun isCapabilityAvailable(key: String): Boolean = when (key) {
        // S0880: the per-type registration buttons + toggles moved into DefaultAppsDialogFragment (not
        // scanned by the layout source); only the launcher remains searchable in the settings screen.
        "btnOpenDefaultAppsDialog" -> mediaCapabilities.supportsDefaultPlayer
        else -> true
    }

    fun search(query: String): List<SettingsSearchIndex> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return entries
        return entries.filter { entry ->
            entry.title.lowercase().contains(normalized) ||
                entry.keywords.any { keyword -> keyword.contains(normalized) }
        }
    }
}
