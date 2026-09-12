package com.sza.fastmediasorter.wear.domain.repository.preferences

import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import kotlinx.coroutines.flow.Flow

/** How the navigation screens are laid out, narrowed and re-entered. */
interface WearBrowsePreferences {

    /** S1781: one view shared by the navigation screens - the home screen and the Resources page. */
    val viewMode: Flow<WearViewMode>
    suspend fun setViewMode(mode: WearViewMode)

    /** S1730: the view of a file list inside a resource, deliberately separate from [viewMode]. */
    val fileListViewMode: Flow<WearViewMode>
    suspend fun setFileListViewMode(mode: WearViewMode)

    /**
     * S2199: how the browse list was last narrowed and ordered, so the choice survives a restart.
     *
     * The search query is deliberately absent: a restored text empties the list on a word the wearer
     * cannot see, which is the same call `StreamsSessionStore` made on the phone.
     */
    val browseContentTypes: Flow<Set<WearContentType>>
    suspend fun setBrowseContentTypes(types: Set<WearContentType>)

    val browseSortOrder: Flow<BrowseSortOrder>
    suspend fun setBrowseSortOrder(order: BrowseSortOrder)

    /**
     * S1781: the resources opened last, newest first. S1836: an entry that predates the identifier
     * cannot address a source and never reaches this list. S1974: a list rather than a single value,
     * because the home screen fills its first row with as many shortcuts as it has columns; an empty
     * list is the whole of "there is no shortcut".
     *
     * [setLastUsedResource] pushes onto that history - an id already in it moves to the front rather
     * than appearing twice - and [clearLastUsedResource] empties it.
     */
    val lastUsedResources: Flow<List<LastUsedResource>>
    suspend fun setLastUsedResource(id: String, name: String)

    /**
     * S2499: pushes a channel onto the same history, so the home row orders folders and channels by
     * one recency instead of merging two.
     *
     * [normalizedUrl] is already normalized by the caller, and by `normalizeWearStreamUrl`
     * specifically: it is the spelling the launch-target resolver compares the channel catalog
     * against, and reopening this shortcut goes through that resolver.
     */
    suspend fun setLastUsedStream(normalizedUrl: String, name: String)
    suspend fun clearLastUsedResource()
}
