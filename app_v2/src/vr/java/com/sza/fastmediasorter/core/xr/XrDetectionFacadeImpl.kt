package com.sza.fastmediasorter.core.xr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real combined-state facade. Folds [XrEnvironmentDetector] output and
 * [MasterTogglePreferences] into one [XrDetectionState] flow.
 */
@Singleton
class XrDetectionFacadeImpl @Inject constructor(
    private val detector: XrEnvironmentDetector,
    private val preferences: MasterTogglePreferences,
) : XrDetectionFacade {

    override fun state(): Flow<XrDetectionState> {
        val env = detector.detect()
        return preferences.enabled
            .map { enabled -> fold(env, enabled) }
            .distinctUntilChanged()
    }

    private fun fold(env: XrEnvironment, enabled: Boolean): XrDetectionState = when (env) {
        XrEnvironment.NONE -> XrDetectionState.NONE
        XrEnvironment.VR_QUEST, XrEnvironment.ANDROID_XR ->
            if (enabled) XrDetectionState.AVAILABLE_ENABLED else XrDetectionState.AVAILABLE_DISABLED_BY_USER
    }
}
