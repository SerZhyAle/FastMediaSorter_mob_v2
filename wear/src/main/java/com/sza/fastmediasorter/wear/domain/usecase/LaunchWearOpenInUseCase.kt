package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.preferences.WearOpenInLastUsedStore
import com.sza.fastmediasorter.wear.domain.model.WearOpenInLaunchResult
import com.sza.fastmediasorter.wear.domain.model.WearOpenInObject
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import com.sza.fastmediasorter.wear.domain.repository.WearOpenInRepository
import javax.inject.Inject

/**
 * S4381: opens [WearOpenInObject] in the chosen application and remembers the choice - but only when the
 * application really started, so a vanished or refusing one never takes the first place in the next list.
 */
class LaunchWearOpenInUseCase @Inject constructor(
    private val repository: WearOpenInRepository,
    private val lastUsedStore: WearOpenInLastUsedStore,
) {

    suspend operator fun invoke(target: WearOpenInTarget, obj: WearOpenInObject): WearOpenInLaunchResult {
        val result = repository.launch(target, obj)
        if (result == WearOpenInLaunchResult.Launched) {
            lastUsedStore.remember(obj.kind, target.packageName)
        }
        return result
    }
}
