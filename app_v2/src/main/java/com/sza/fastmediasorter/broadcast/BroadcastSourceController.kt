package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import kotlinx.coroutines.flow.StateFlow

sealed interface BroadcastState {
    data object Idle : BroadcastState
    data class Live(val descriptor: BroadcastDescriptorDto) : BroadcastState
    data class Failed(val reason: String) : BroadcastState
}

/**
 * Controller managing broadcast session lifecycle (capturing audio/video and serving it over network).
 */
interface BroadcastSourceController {
    val isAvailable: Boolean
    val state: StateFlow<BroadcastState>

    fun start(mode: BroadcastMode)
    fun stop()
}
