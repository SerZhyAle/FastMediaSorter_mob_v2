package com.sza.fastmediasorter.broadcast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op implementation of [BroadcastSourceController] for builds where broadcast source capability is disabled.
 */
@Singleton
class NoOpBroadcastSourceController @Inject constructor() : BroadcastSourceController {
    override val isAvailable: Boolean = false

    private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
    override val state: StateFlow<BroadcastState> = _state.asStateFlow()

    private val _listenerCount = MutableStateFlow(0)
    override val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

    override fun start(mode: BroadcastMode, lensId: String?) {
        // No-op
    }

    override fun stop() {
        // No-op
    }

    override fun acknowledgeFailure() {
        // No-op
    }

    override fun toggleCamera() {
        // No-op
    }

    override fun toggleMicrophone() {
        // No-op
    }

    override fun selectLens(lensId: String) {
        // No-op
    }
}
