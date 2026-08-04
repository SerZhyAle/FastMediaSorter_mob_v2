package com.sza.fastmediasorter.widget

import android.app.Activity
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.SaveCapturedMediaUseCase
import com.sza.fastmediasorter.ui.cameracapture.helpers.CameraLocationProvider
import com.sza.fastmediasorter.ui.cameracapture.helpers.HeadlessPhotoCapturer
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1195: owns the domain dependencies of [PhotoCaptureLaunchManager] so the
 * [PhotoCaptureLaunchActivity] trampoline injects neither a repository nor a use case (CLAUDE.md
 * Rule 3). The host still supplies its own lifecycle-bound pieces, which Hilt cannot provide.
 */
class PhotoCaptureLaunchManagerFactory @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val mediaCapabilities: MediaCapabilities,
    private val saveCapturedMedia: SaveCapturedMediaUseCase,
) {

    @Suppress("LongParameterList") // Mirrors the manager's host-supplied surface one-to-one.
    fun create(
        activity: Activity,
        coroutineScope: CoroutineScope,
        autoAction: String?,
        capturer: HeadlessPhotoCapturer,
        locationProvider: CameraLocationProvider,
        requestPermission: () -> Unit,
        finish: () -> Unit,
    ): PhotoCaptureLaunchManager = PhotoCaptureLaunchManager(
        activity = activity,
        settingsRepository = settingsRepository,
        mediaCapabilities = mediaCapabilities,
        saveCapturedMedia = saveCapturedMedia,
        coroutineScope = coroutineScope,
        autoAction = autoAction,
        capturer = capturer,
        locationProvider = locationProvider,
        requestPermission = requestPermission,
        finish = finish,
    )
}
