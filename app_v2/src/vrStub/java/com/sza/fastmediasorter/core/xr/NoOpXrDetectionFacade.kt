package com.sza.fastmediasorter.core.xr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op facade used by phone-only flavors. Emits [XrDetectionState.NONE] and completes.
 *
 * Paired with the real `XrDetectionFacadeImpl` in `src/vr/java/`.
 */
@Singleton
class NoOpXrDetectionFacade @Inject constructor() : XrDetectionFacade {
    override fun state(): Flow<XrDetectionState> = flowOf(XrDetectionState.NONE)
}
