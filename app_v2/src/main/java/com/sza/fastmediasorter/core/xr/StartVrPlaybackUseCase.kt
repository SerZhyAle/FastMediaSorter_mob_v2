package com.sza.fastmediasorter.core.xr

/**
 * Shared preflight contract for every UI surface that can ask for immersive playback.
 * Launcher registration stays in the caller; this use-case only turns a caller-level request
 * into either a launchable [VrLaunchInput] or a terminal [VrLaunchResult].
 */
interface StartVrPlaybackUseCase {

    sealed interface Result {
        data class Ready(val input: VrLaunchInput) : Result
        data class Completed(val result: VrLaunchResult) : Result
    }

    /**
     * Legacy direct-dispatch bridge kept for player-panel task handoff code that still relies
     * on Home + PendingIntent return. New ActivityResult callers should use [Result] instead.
     */
    sealed interface DispatchResult {
        data object Started : DispatchResult
        data class Unavailable(val reason: VrLaunchUnavailableReason) : DispatchResult
        data class Failed(val message: String? = null) : DispatchResult
    }

    suspend operator fun invoke(request: StartVrPlaybackRequest): Result

    suspend operator fun invoke(
        request: StartVrPlaybackRequest,
        returnTarget: VrPanelReturnTarget?,
    ): DispatchResult
}
