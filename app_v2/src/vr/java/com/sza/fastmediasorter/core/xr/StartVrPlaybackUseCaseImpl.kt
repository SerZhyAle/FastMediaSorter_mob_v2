package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import timber.log.Timber

@Singleton
class StartVrPlaybackUseCaseImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detectionFacade: XrDetectionFacade,
    private val entryGateway: XrEntryGateway,
    private val payloadHolder: VrLaunchPayloadHolder,
) : StartVrPlaybackUseCase {

    override suspend fun invoke(request: StartVrPlaybackRequest): StartVrPlaybackUseCase.Result {
        val normalizedRequest = normalizeRequest(request)
        val unavailable = preflightUnavailableReason(normalizedRequest)
        if (unavailable != null) {
            Timber.i(
                "VR launch preflight completed source=%s mode=%s mediaType=%s result=unavailable reason=%s",
                normalizedRequest.source,
                normalizedRequest.launchMode,
                normalizedRequest.mediaType,
                unavailable,
            )
            return StartVrPlaybackUseCase.Result.Completed(
                VrLaunchResult.Unavailable(unavailable)
            )
        }

        val input = VrLaunchInput.fromRequest(normalizedRequest)
        val intent = entryGateway.createImmersiveIntent(input)
        if (intent == null) {
            Timber.i(
                "VR launch preflight completed source=%s mode=%s mediaType=%s result=unavailable reason=%s",
                normalizedRequest.source,
                normalizedRequest.launchMode,
                normalizedRequest.mediaType,
                VrLaunchUnavailableReason.NoRuntime,
            )
            return StartVrPlaybackUseCase.Result.Completed(
                VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime)
            )
        }

        Timber.i(
            "VR launch preflight completed source=%s mode=%s mediaType=%s result=ready",
            normalizedRequest.source,
            normalizedRequest.launchMode,
            normalizedRequest.mediaType,
        )
        return StartVrPlaybackUseCase.Result.Ready(input)
    }

    override suspend fun invoke(
        request: StartVrPlaybackRequest,
        returnTarget: VrPanelReturnTarget?,
    ): StartVrPlaybackUseCase.DispatchResult {
        val result = invoke(request)
        if (result is StartVrPlaybackUseCase.Result.Completed) {
            return result.result.toDispatchResult()
        }

        val input = (result as StartVrPlaybackUseCase.Result.Ready).input
        val intent = entryGateway.createImmersiveIntent(input)
            ?: return StartVrPlaybackUseCase.DispatchResult.Unavailable(
                VrLaunchUnavailableReason.NoRuntime
            )

        return try {
            if (returnTarget != null) {
                intent.putExtra(VrLaunchInput.EXTRA_RETURN_TARGET_TOKEN, payloadHolder.put(returnTarget))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            Timber.i(
                "VR launch dispatched source=%s mode=%s mediaType=%s",
                request.source,
                input.launchMode,
                input.mediaType,
            )
            StartVrPlaybackUseCase.DispatchResult.Started
        } catch (t: Throwable) {
            Timber.e(
                t,
                "VR launch dispatch failed source=%s mode=%s mediaType=%s",
                request.source,
                input.launchMode,
                input.mediaType,
            )
            StartVrPlaybackUseCase.DispatchResult.Failed(t.message)
        }
    }

    private suspend fun preflightUnavailableReason(
        request: StartVrPlaybackRequest,
    ): VrLaunchUnavailableReason? {
        return when (detectionFacade.state().first()) {
            XrDetectionState.NONE -> VrLaunchUnavailableReason.NoRuntime
            XrDetectionState.AVAILABLE_DISABLED_BY_USER -> VrLaunchUnavailableReason.DisabledByUser
            XrDetectionState.AVAILABLE_ENABLED -> validateRequest(request)
        }
    }

    private fun validateRequest(request: StartVrPlaybackRequest): VrLaunchUnavailableReason? {
        if (request.launchMode != VrLaunchMode.FILE_URI) {
            return null
        }
        val uri = request.fileUriString
        if (uri.isNullOrBlank()) {
            return VrLaunchUnavailableReason.InvalidUri
        }
        return when (request.mediaType) {
            VrMediaType.IMAGE -> null
            VrMediaType.VIDEO -> {
                val isLocal = uri.startsWith("file://") || uri.startsWith("/")
                if (isLocal) null else VrLaunchUnavailableReason.InvalidUri
            }
            VrMediaType.GIF -> VrLaunchUnavailableReason.NotYetSupported
        }
    }

    private fun normalizeRequest(request: StartVrPlaybackRequest): StartVrPlaybackRequest {
        return if (request.launchMode == VrLaunchMode.DIAGNOSTIC_PLAYLIST) {
            request.copy(
                fileUriString = null,
                mediaType = VrMediaType.IMAGE,
            )
        } else {
            request
        }
    }

    private fun VrLaunchResult.toDispatchResult(): StartVrPlaybackUseCase.DispatchResult {
        return when (this) {
            is VrLaunchResult.Unavailable ->
                StartVrPlaybackUseCase.DispatchResult.Unavailable(reason)
            is VrLaunchResult.Crashed ->
                StartVrPlaybackUseCase.DispatchResult.Failed(reason)
            VrLaunchResult.CancelledByUser,
            VrLaunchResult.CompletedNormally ->
                StartVrPlaybackUseCase.DispatchResult.Failed()
        }
    }
}
