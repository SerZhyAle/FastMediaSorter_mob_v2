package com.sza.fastmediasorter.core.xr

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoOpStartVrPlaybackUseCase @Inject constructor() : StartVrPlaybackUseCase {

    override suspend fun invoke(request: StartVrPlaybackRequest): StartVrPlaybackUseCase.Result {
        return StartVrPlaybackUseCase.Result.Completed(
            VrLaunchResult.Unavailable(VrLaunchUnavailableReason.NoRuntime)
        )
    }

    override suspend fun invoke(
        request: StartVrPlaybackRequest,
        returnTarget: VrPanelReturnTarget?,
    ): StartVrPlaybackUseCase.DispatchResult {
        return StartVrPlaybackUseCase.DispatchResult.Unavailable(
            VrLaunchUnavailableReason.NoRuntime
        )
    }
}
