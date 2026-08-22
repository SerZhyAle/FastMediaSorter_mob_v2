package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject

/**
 * S1836: the home shortcut may only point at a source that is still there.
 *
 * A remembered source can be deleted on the watch or stop arriving from the phone, and an entry written
 * by a build that stored only the name carries no identifier at all. Both surface here as null, so the
 * home screen has one value to render instead of a second rule to keep in step with the first.
 */
class ResolveLastUsedResourceUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val networkSourceRepository: NetworkSourceRepository
) {

    operator fun invoke(): Flow<LastUsedResource?> = combine(
        preferencesRepository.lastUsedResource,
        networkSourceRepository.observeSources()
    ) { remembered, sources ->
        val resolved = remembered?.takeIf { target -> sources.any { it.id == target.id } }
        Timber.d("S1836: home shortcut resolves to ${resolved?.name}")
        resolved
    }
}
