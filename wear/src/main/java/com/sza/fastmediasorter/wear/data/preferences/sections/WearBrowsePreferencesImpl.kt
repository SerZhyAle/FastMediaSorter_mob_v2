package com.sza.fastmediasorter.wear.data.preferences.sections

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.wear.data.preferences.LastUsedResourceHistory
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceKeys
import com.sza.fastmediasorter.wear.data.preferences.WearPreferenceSection
import com.sza.fastmediasorter.wear.data.preferences.WearSettingsDataStore
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.WearComplicationKind
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.preferences.WearBrowsePreferences
import com.sza.fastmediasorter.wear.domain.usecase.RequestWearComplicationRefreshUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearBrowsePreferencesImpl @Inject constructor(
    settings: WearSettingsDataStore,
    private val requestWearComplicationRefreshUseCase: RequestWearComplicationRefreshUseCase
) : WearPreferenceSection(settings), WearBrowsePreferences {

    override val viewMode: Flow<WearViewMode> = store.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[WearPreferenceKeys.VIEW_MODE])
    }

    override suspend fun setViewMode(mode: WearViewMode) {
        stampedEdit("viewMode") { prefs ->
            prefs[WearPreferenceKeys.VIEW_MODE] = mode.name
        }
    }

    // S1730: its own key, never wear_view_mode - the home screen stays a list while a photo folder
    // is a grid, which one shared value cannot express.
    override val fileListViewMode: Flow<WearViewMode> = store.data.map { prefs ->
        WearViewMode.fromNameOrDefault(prefs[WearPreferenceKeys.FILE_LIST_VIEW_MODE])
    }

    override suspend fun setFileListViewMode(mode: WearViewMode) {
        stampedEdit("fileListViewMode") { prefs ->
            prefs[WearPreferenceKeys.FILE_LIST_VIEW_MODE] = mode.name
        }
    }

    // S2199: browse-list refine state. Written with a plain edit and never through stampedEdit -
    // that call enters a value into the phone-watch settings exchange, and how one list was last
    // narrowed is session state the wearer set on this device, not a setting to replicate.
    // A name that no longer parses is dropped: an enum constant renamed later must not stop the
    // list from opening, and dropping degrades to "no filter", the safe direction to be wrong in.
    override val browseContentTypes: Flow<Set<WearContentType>> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.BROWSE_CONTENT_TYPES]
            ?.mapNotNull { name -> runCatching { WearContentType.valueOf(name) }.getOrNull() }
            ?.toSet()
            .orEmpty()
    }

    override suspend fun setBrowseContentTypes(types: Set<WearContentType>) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.BROWSE_CONTENT_TYPES] = types.map { it.name }.toSet()
        }
    }

    override val browseSortOrder: Flow<BrowseSortOrder> = store.data.map { prefs ->
        prefs[WearPreferenceKeys.BROWSE_SORT_ORDER]
            ?.let { name -> runCatching { BrowseSortOrder.valueOf(name) }.getOrNull() }
            ?: BrowseSortOrder.DEFAULT
    }

    override suspend fun setBrowseSortOrder(order: BrowseSortOrder) {
        store.edit { prefs ->
            prefs[WearPreferenceKeys.BROWSE_SORT_ORDER] = order.name
        }
    }

    // S1836: an entry is emitted only when both halves are stored. An install upgraded from a build
    // that kept the name alone holds no identifier, and a caption that addresses nothing is not a
    // shortcut. S1974: the single stored pair became a history, and the two legacy keys are read as
    // its seed so an upgraded install keeps the shortcut it already had.
    override val lastUsedResources: Flow<List<LastUsedResource>> = store.data.map { prefs ->
        val stored = prefs[WearPreferenceKeys.LAST_USED_RESOURCES]
        if (stored == null) legacyLastUsedResource(prefs) else LastUsedResourceHistory.decode(stored)
    }

    private fun legacyLastUsedResource(prefs: Preferences): List<LastUsedResource> {
        val id = prefs[WearPreferenceKeys.LAST_USED_RESOURCE_ID]
        val name = prefs[WearPreferenceKeys.LAST_USED_RESOURCE]
        return if (id == null || name == null) emptyList() else listOf(LastUsedResource(id, name))
    }

    override suspend fun setLastUsedResource(id: String, name: String) =
        pushLastUsed(LastUsedResource(id, name, LastUsedKind.RESOURCE))

    override suspend fun setLastUsedStream(normalizedUrl: String, name: String) =
        pushLastUsed(LastUsedResource(normalizedUrl, name, LastUsedKind.STREAM))

    /**
     * S2499: one body for both kinds, so the legacy fallback and the complication refresh cannot end
     * up applying to a folder and not to a channel.
     */
    private suspend fun pushLastUsed(entry: LastUsedResource) {
        store.edit { prefs ->
            val current = prefs[WearPreferenceKeys.LAST_USED_RESOURCES]
                ?.let { LastUsedResourceHistory.decode(it) }
                ?: legacyLastUsedResource(prefs)
            val pushed = LastUsedResourceHistory.push(current, entry)
            prefs[WearPreferenceKeys.LAST_USED_RESOURCES] = LastUsedResourceHistory.encode(pushed)
        }
        requestWearComplicationRefreshUseCase.invoke(WearComplicationKind.LAST_RESOURCE)
    }

    override suspend fun clearLastUsedResource() {
        store.edit { prefs ->
            prefs.remove(WearPreferenceKeys.LAST_USED_RESOURCES)
            // The legacy pair is removed too: it is the fallback the reader falls back to, so leaving
            // it behind would resurrect the shortcut the caller just cleared.
            prefs.remove(WearPreferenceKeys.LAST_USED_RESOURCE_ID)
            prefs.remove(WearPreferenceKeys.LAST_USED_RESOURCE)
        }
        requestWearComplicationRefreshUseCase.invoke(WearComplicationKind.LAST_RESOURCE)
    }
}
