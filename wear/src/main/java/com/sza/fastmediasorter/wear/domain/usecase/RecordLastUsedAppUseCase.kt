package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import javax.inject.Inject

/**
 * S3116: records which mini-program was opened last, for the home row that offers it again.
 *
 * The only writer of that preference. A program is reached from four places - the Apps list, the home
 * row itself, a tile shortcut and an external launch intent - and a write at each of them is four
 * chances to record a different thing, or nothing at all; the navigation host calls this once for all
 * four (strategic ADR-1).
 */
class RecordLastUsedAppUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository
) {

    suspend operator fun invoke(id: WearAppId) {
        preferencesRepository.setLastUsedApp(id)
    }
}
