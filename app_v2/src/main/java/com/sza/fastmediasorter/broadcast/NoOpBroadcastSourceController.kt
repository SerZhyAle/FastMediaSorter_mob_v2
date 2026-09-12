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

    override fun start(mode: BroadcastMode) {
        // No-op
    }

    override fun stop() {
        // No-op
    }

    override fun acknowledgeFailure() {
        // No-op
    }
}
