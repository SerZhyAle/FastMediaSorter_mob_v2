package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
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
 * not on app cold start.
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

    private val allEntries: List<SettingsSearchIndex> by lazy {
        val raw = source.collect()
        val enriched = raw.mapNotNull { collector.enrich(it) }
        enriched
    }

    val entries: List<SettingsSearchIndex>
        get() = allEntries.filter { entry ->
            availability.isAvailable(entry) &&
                capabilityGate.isAvailable(entry) &&
                deviceFeatureGate.isAvailable(entry.key) &&
                isCapabilityAvailable(entry.key)
        }

    private fun isCapabilityAvailable(key: String): Boolean = when (key) {
        "rowPrimaryMediaPlayer", "rowAcceptSharedFiles" -> mediaCapabilities.supportsDefaultPlayer
        "btnSettingsDefaultPlayerImages" -> mediaCapabilities.supportsDefaultPlayer && mediaCapabilities.supportsImages
        "btnSettingsDefaultPlayerAudio" -> mediaCapabilities.supportsDefaultPlayer && mediaCapabilities.supportsAudio
        "btnSettingsDefaultPlayerVideo" -> mediaCapabilities.supportsDefaultPlayer && mediaCapabilities.supportsVideo
        "btnSettingsDefaultPlayerDocs" -> mediaCapabilities.supportsDefaultPlayer && mediaCapabilities.supportsDocuments
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
