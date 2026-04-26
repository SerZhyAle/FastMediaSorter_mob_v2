package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.network.model.ConnectionKey
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks PLAYER connection lifecycle state and watchdog timestamps across
 * SmbDataSource instances. ExoPlayer creates a new DataSource per retry, so
 * per-instance fields cannot carry state between attempts — this singleton does.
 *
 * Lifecycle:
 *   FRESH       — connection just created, no successful file open yet
 *   VALIDATED   — openFile() succeeded at least once on this connection
 *   SUSPECT     — transport error or watchdog fired; connection will be invalidated
 *   INVALIDATED — invalidateExoPlayerConnection() called; pool entry removed
 */
@Singleton
class SmbPlaybackConnectionTracker @Inject constructor() {

    enum class PlaybackConnectionState { FRESH, VALIDATED, SUSPECT, INVALIDATED }

    private val states = ConcurrentHashMap<ConnectionKey, PlaybackConnectionState>()
    private val watchdogTimestamps = ConcurrentHashMap<ConnectionKey, Long>()

    companion object {
        private const val WATCHDOG_WINDOW_MS = 60_000L
    }

    fun onConnectionCreated(key: ConnectionKey) {
        states[key] = PlaybackConnectionState.FRESH
    }

    fun onConnectionValidated(key: ConnectionKey) {
        states[key] = PlaybackConnectionState.VALIDATED
    }

    fun onConnectionInvalidated(key: ConnectionKey) {
        Timber.d("[SMB-PLAY] state ${states[key] ?: "absent"} → INVALIDATED for ${key.server}")
        states.remove(key)
    }

    /** Current state name for log context — avoids exposing enum across package boundary. */
    fun getStateName(key: ConnectionKey): String = states[key]?.name ?: "absent"

    fun recordWatchdog(key: ConnectionKey) {
        watchdogTimestamps[key] = System.currentTimeMillis()
        Timber.w("[SMB-PLAY] watchdog recorded for ${key.server} (state=${getStateName(key)})")
    }

    fun clearWatchdog(key: ConnectionKey) {
        watchdogTimestamps.remove(key)
    }

    /** True if a watchdog fired for [key] within the last 60 s — triggers fail-fast. */
    fun isRecentWatchdog(key: ConnectionKey): Boolean {
        val ts = watchdogTimestamps[key] ?: return false
        return (System.currentTimeMillis() - ts) < WATCHDOG_WINDOW_MS
    }

    /** Called on network reset or manual pool clear — allows fresh attempts after recovery. */
    fun clearAll() {
        states.clear()
        watchdogTimestamps.clear()
        Timber.d("[SMB-PLAY] tracker cleared (network reset or manual)")
    }
}
