package com.sza.fastmediasorter.widget

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.data.capture.CameraCaptureSaver
import com.sza.fastmediasorter.data.transfer.UnifiedFileOperationHandler
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1329: owns the injected singletons of [CameraQuickCaptureLaunchManager] so the
 * [CameraQuickCaptureActivity] trampoline injects no repository of its own (CLAUDE.md Rule 3). The
 * host still supplies `appWidgetId`, its scope and its result lambdas, which Hilt cannot provide.
 *
 * Mirrors the sibling [PhotoCaptureLaunchManagerFactory] shape one-to-one.
 */
class CameraQuickCaptureLaunchManagerFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cameraCaptureSaver: CameraCaptureSaver,
    private val fileOperationHandler: UnifiedFileOperationHandler,
) {

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun create(
        activity: Activity,
        appWidgetId: Int,
        coroutineScope: CoroutineScope,
        requestPermission: () -> Unit,
        launchCapture: (Intent) -> Unit,
        finish: () -> Unit,
    ): CameraQuickCaptureLaunchManager = CameraQuickCaptureLaunchManager(
        activity = activity,
        appWidgetId = appWidgetId,
        settingsRepository = settingsRepository,
        cameraCaptureSaver = cameraCaptureSaver,
        fileOperationHandler = fileOperationHandler,
        coroutineScope = coroutineScope,
        requestPermission = requestPermission,
        launchCapture = launchCapture,
        finish = finish,
    )
}
