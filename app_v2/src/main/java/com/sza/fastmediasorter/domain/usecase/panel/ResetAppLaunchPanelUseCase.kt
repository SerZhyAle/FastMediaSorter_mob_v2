package com.sza.fastmediasorter.domain.usecase.panel

import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Restores the app-launch panel to the install-time default set (S0663). Clears every configured
 * slot, then re-runs the first-run seed - the seed's "no-op when tiles exist" guard passes because
 * the panel is empty again, so it repopulates FMS + the curated feature/OS defaults.
 */
class ResetAppLaunchPanelUseCase @Inject constructor(
    private val repository: AppLaunchPanelRepository,
    private val seedDefault: SeedDefaultAppLaunchPanelUseCase,
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        repository.replaceAll(emptyList())
        seedDefault()
    }
}
