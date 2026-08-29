package com.sza.fastmediasorter.ui.launcher.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LauncherIdleState {
    ACTIVE,
    DIMMING,
    BLACKOUT
}

@Singleton
class LauncherIdleManager @Inject constructor() {

    private val _idleState = MutableStateFlow(LauncherIdleState.ACTIVE)
    val idleState: StateFlow<LauncherIdleState> = _idleState.asStateFlow()

    private var isVideoPlaying: Boolean = false

    fun onUserInteraction() {
        if (_idleState.value != LauncherIdleState.ACTIVE) {
            _idleState.value = LauncherIdleState.ACTIVE
        }
    }

    fun setVideoPlaying(active: Boolean) {
        isVideoPlaying = active
        if (active && _idleState.value != LauncherIdleState.ACTIVE) {
            _idleState.value = LauncherIdleState.ACTIVE
        }
    }

    fun updateIdleState(newState: LauncherIdleState) {
        if (isVideoPlaying && newState != LauncherIdleState.ACTIVE) {
            return
        }
        _idleState.value = newState
    }
}
