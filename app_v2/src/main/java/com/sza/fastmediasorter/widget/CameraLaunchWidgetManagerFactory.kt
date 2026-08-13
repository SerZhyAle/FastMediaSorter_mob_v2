package com.sza.fastmediasorter.widget

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1329: owns the domain dependencies of [CameraLaunchWidgetManager] so the [CameraLaunchActivity]
 * trampoline injects neither a repository nor a capability probe (CLAUDE.md Rule 3). The host still
 * supplies its own lifecycle-bound pieces, which Hilt cannot provide.
 *
 * Mirrors the sibling [PhotoCaptureLaunchManagerFactory] shape one-to-one.
 */
class CameraLaunchWidgetManagerFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
) {

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun create(
        activity: Activity,
        coroutineScope: CoroutineScope,
        forceVideo: Boolean,
        requestPermission: () -> Unit,
        launchCapture: (Intent) -> Unit,
        finish: () -> Unit,
    ): CameraLaunchWidgetManager = CameraLaunchWidgetManager(
        activity = activity,
        settingsRepository = settingsRepository,
        mediaCapabilities = mediaCapabilities,
        coroutineScope = coroutineScope,
        forceVideo = forceVideo,
        requestPermission = requestPermission,
        launchCapture = launchCapture,
        finish = finish,
    )
}
