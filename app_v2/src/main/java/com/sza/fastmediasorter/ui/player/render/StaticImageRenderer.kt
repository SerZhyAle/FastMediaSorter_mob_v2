package com.sza.fastmediasorter.ui.player.render

import com.sza.fastmediasorter.domain.model.StereoMode
import kotlinx.coroutines.flow.StateFlow

enum class RendererMode {
    MANUAL,
    SLIDESHOW
}

sealed interface RenderState {
    data object Idle : RenderState
    data class Loading(val target: RenderTarget) : RenderState
    data class Ready(val target: RenderTarget) : RenderState
    data class Transitioning(
        val from: RenderTarget,
        val to: RenderTarget,
        val policy: TransitionPolicy
    ) : RenderState
    data class Error(
        val target: RenderTarget,
        val cause: Throwable
    ) : RenderState
}

interface StaticImageRenderer {
    val state: StateFlow<RenderState>

    suspend fun render(target: RenderTarget)
    suspend fun prefetch(targets: List<RenderTarget>)
    fun setMode(mode: RendererMode)
    /** Set the stereo crop mode for 3D images (SBS / OU). MONO = no crop. */
    fun setStereoMode(mode: StereoMode)
    fun onPause()
    fun onResume()
    fun release()
}
