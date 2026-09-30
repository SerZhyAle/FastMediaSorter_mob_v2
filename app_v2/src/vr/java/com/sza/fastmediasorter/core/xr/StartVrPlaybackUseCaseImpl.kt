package com.sza.fastmediasorter.core.xr

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartVrPlaybackUseCaseImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detectionFacade: XrDetectionFacade,
    private val entryGateway: XrEntryGateway,
    private val payloadHolder: VrLaunchPayloadHolder,
) : StartVrPlaybackUseCase {

    override suspend fun invoke(request: StartVrPlaybackRequest): StartVrPlaybackUseCase.Result =
        prepare(request).first

    // The gateway stores a launch-input token on every createImmersiveIntent call, so the Intent
    // built to answer readiness is the one the dispatch overload starts; building it again left one
    // never-consumed token per launch in the bounded payload holder, evicting live ones.
    private suspend fun prepare(
        request: StartVrPlaybackRequest,
    ): Pair<StartVrPlaybackUseCase.Result, Intent?> {
        val normalizedRequest = normalizeRequest(request)
        val input = VrLaunchInput.fromRequest(normalizedRequest)
        val preflightReason = preflightUnavailableReason(normalizedRequest)
        val intent = if (preflightReason == null) entryGateway.createImmersiveIntent(input) else null
        val unavailable = preflightReason ?: VrLaunchUnavailableReason.NoRuntime.takeIf { intent == null }
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
            ) to null
        }

        Timber.i(
            "VR launch preflight completed source=%s mode=%s mediaType=%s result=ready",
            normalizedRequest.source,
            normalizedRequest.launchMode,
            normalizedRequest.mediaType,
        )
        return StartVrPlaybackUseCase.Result.Ready(input) to intent
    }

    override suspend fun invoke(
        request: StartVrPlaybackRequest,
        returnTarget: VrPanelReturnTarget?,
    ): StartVrPlaybackUseCase.DispatchResult {
        val (result, preparedIntent) = prepare(request)
        if (result is StartVrPlaybackUseCase.Result.Completed) {
            return result.result.toDispatchResult()
        }

        val input = (result as StartVrPlaybackUseCase.Result.Ready).input
        val intent = preparedIntent
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
        if (request.launchMode == VrLaunchMode.RESOURCE_BROWSE) {
            return if (request.resourceId == null) VrLaunchUnavailableReason.InvalidUri else null
        }
        if (request.launchMode != VrLaunchMode.FILE_URI) {
            return null
        }
        val uri = request.fileUriString
        if (uri.isNullOrBlank()) {
            return VrLaunchUnavailableReason.InvalidUri
        }
        return when (request.mediaType) {
            VrMediaType.IMAGE -> null
            VrMediaType.VIDEO -> validateVideoLaunchUri(uri, request.sourceKind)
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
