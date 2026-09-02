package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 */
class ResolveLastUsedResourceUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val networkSourceRepository: NetworkSourceRepository
) {

    operator fun invoke(): Flow<List<LastUsedResource>> = combine(
        preferencesRepository.lastUsedResources,
        networkSourceRepository.observeSources()
    ) { remembered, sources ->
        // S2129: the same lookup that proves the source still exists also carries its icon across.
        // Enriching here rather than in the stored history keeps the icon current when the owner
        // repoints it on the phone, and leaves the two-field history format untouched.
        remembered.mapNotNull { target ->
            sources.firstOrNull { it.id == target.id }
                ?.let { target.copy(iconId = it.iconId) }
        }
    }
}
