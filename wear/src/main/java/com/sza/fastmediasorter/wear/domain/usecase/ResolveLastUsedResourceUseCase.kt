package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedKind
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.normalizeWearStreamUrl
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject

/**
 * S1836: a home shortcut may only point at a source that is still there.
 *
 * A remembered source can be deleted on the watch or stop arriving from the phone, and an entry written
 * by a build that stored only the name carries no identifier at all. Both are dropped here, so the home
 * screen has one list to render instead of a second rule to keep in step with the first.
 *
 * S1974: a list rather than a single value, and the stored order - newest first - is preserved, because
 * the screen fills its first row left to right with as many of these as it has columns.
 *
 * S2499: an entry can also name a channel, and the store that proves it still exists is chosen by the
 * entry's kind. The drop rule stays one rule for both: a target the store no longer lists leaves the
 * row rather than becoming a cell that addresses nothing.
 */
class ResolveLastUsedResourceUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val networkSourceRepository: NetworkSourceRepository,
    private val streamChannelRepository: WearStreamChannelRepository
) {

    operator fun invoke(): Flow<List<LastUsedResource>> = combine(
        preferencesRepository.lastUsedResources,
        networkSourceRepository.observeSources(),
        streamChannelRepository.observeChannels()
    ) { remembered, sources, channels ->
        val resolved = remembered.mapNotNull { target ->
            when (target.kind) {
                LastUsedKind.RESOURCE -> target.resolveAgainstSources(sources)
                LastUsedKind.STREAM -> target.resolveAgainstChannels(channels)
            }
        }
        resolved
    }

    /**
     * S2129: the same lookup that proves the source still exists also carries its icon across.
     * Enriching here rather than in the stored history keeps the icon current when the owner
     * repoints it on the phone, and leaves the stored record's fields untouched.
     */
    private fun LastUsedResource.resolveAgainstSources(sources: List<NetworkSource>): LastUsedResource? =
        sources.firstOrNull { it.id == id }?.let { copy(iconId = it.iconId) }

    /**
     * S2499: the caption and favicon index are taken from the live catalog row, which keeps a renamed
     * or re-indexed channel current on the home screen.
     */
    private fun LastUsedResource.resolveAgainstChannels(channels: List<WearStreamChannel>): LastUsedResource? =
        channels.firstOrNull { normalizeWearStreamUrl(it.url) == id }?.let {
            Timber.d("S2499: resolved channel %s with favicon %s", it.name, it.faviconIndex)
            copy(name = it.name, faviconIndex = it.faviconIndex)
        }
}
