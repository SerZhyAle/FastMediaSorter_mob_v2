package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.preferences.WearOpenInLastUsedStore
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import com.sza.fastmediasorter.wear.domain.openin.WearOpenInOrdering
import com.sza.fastmediasorter.wear.domain.repository.WearOpenInRepository
import javax.inject.Inject

/** S4381: the rows of the "Open in.." list for one object, in the order of strategic ADR-4. */
class ListWearOpenInTargetsUseCase @Inject constructor(
    private val repository: WearOpenInRepository,
    private val lastUsedStore: WearOpenInLastUsedStore,
) {

    suspend operator fun invoke(obj: WearOpenInObject): List<WearOpenInTarget> {
        val found = repository.candidatesFor(obj)
        return WearOpenInOrdering.order(
            candidates = found.apps,
            defaultBrowser = found.defaultBrowser,
            defaultMaps = found.defaultMaps,
            lastUsed = lastUsedStore.lastUsed(obj.kind),
        )
    }
}
