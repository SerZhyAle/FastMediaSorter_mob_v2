package com.sza.fastmediasorter.ui.player.render

import com.sza.fastmediasorter.domain.model.StereoMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NoOpStaticImageRenderer : StaticImageRenderer {
    private val _state = MutableStateFlow<RenderState>(RenderState.Idle)
    override val state: StateFlow<RenderState> = _state

    override suspend fun render(target: RenderTarget) {
        _state.value = RenderState.Loading(target)
        _state.value = RenderState.Ready(target)
    }

    override suspend fun prefetch(targets: List<RenderTarget>) = Unit

    override fun setMode(mode: RendererMode) = Unit

    override fun setStereoMode(mode: StereoMode) = Unit

    override fun setPanelStereoSingleEyeEnabled(enabled: Boolean) = Unit

    override fun onPause() = Unit

    override fun onResume() = Unit

    override fun release() {
        _state.value = RenderState.Idle
    }
}
