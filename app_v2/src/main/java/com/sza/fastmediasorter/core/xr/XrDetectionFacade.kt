package com.sza.fastmediasorter.core.xr

import kotlinx.coroutines.flow.Flow

/**
 * Read-only facade combining [XrEnvironmentDetector] output with the user's master-toggle
 * preference. Consumers observe this single flow and react to its [XrDetectionState] value.
 *
 * Implementations:
 * - `src/vrStub/java/.../core/xr/NoOpXrDetectionFacade` - emits [XrDetectionState.NONE] once.
 * - `src/vr/java/.../core/xr/XrDetectionFacadeImpl` - combines detector + DataStore preference.
 */
interface XrDetectionFacade {
    fun state(): Flow<XrDetectionState>
}
