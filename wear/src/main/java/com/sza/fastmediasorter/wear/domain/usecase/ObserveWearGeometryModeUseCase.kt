package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.capability.WearGeometryDefaults
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S2773: the single place that turns "which view did the owner choose" plus "which view does this
 * build start on" into one answer.
 *
 * The two sources are combined exactly once so the question "which geometry is in force" has one
 * answer that does not depend on whether the user ever opened the settings (strategic 5.1 pillar 2).
 * A caller reading the stored choice instead would see null on every fresh install and lay the watch
 * out with whatever it happened to default to.
 */
class ObserveWearGeometryModeUseCase @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val geometryDefaults: WearGeometryDefaults
) {

    operator fun invoke(): Flow<WearGeometryMode> = preferencesRepository.storedGeometryMode
        .map { stored -> stored ?: geometryDefaults.startingMode }
}
