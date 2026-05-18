package com.sza.fastmediasorter.data.network

import com.sza.fastmediasorter.data.network.model.ConnectionKey
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks PLAYER connection lifecycle state and watchdog timestamps across
 * SmbDataSource instances. ExoPlayer creates a new DataSource per retry, so
 * per-instance fields cannot carry state between attempts - this singleton does.
 *
 * Lifecycle:
 *   FRESH       - connection just created, no successful file open yet
 *   VALIDATED   - openFile() succeeded at least once on this connection
 *   SUSPECT     - transport error or watchdog fired; connection will be invalidated
 *   INVALIDATED - invalidateExoPlayerConnection() called; pool entry removed
 *
 * S0148: the watchdog "fail-fast" lockout is attempt-scoped, not server-scoped.
 * A watchdog fired while opening/reading a file blocks only retries of *that same
 * file* (ExoPlayer recreates the DataSource with the same media-item URI on retry);
 * advancing to a different file on the same server gets an honest attempt. Only when
 * the watchdog keeps firing for *different* files on the same server within the
 * window (server genuinely stalled) does the lockout escalate to server-wide, to
 * avoid a chain of multi-second open hangs.
 */
@Singleton
class SmbPlaybackConnectionTracker @Inject constructor() {

    enum class PlaybackConnectionState { FRESH, VALIDATED, SUSPECT, INVALIDATED }

    private val states = ConcurrentHashMap<ConnectionKey, PlaybackConnectionState>()
    private val watchdogs = ConcurrentHashMap<ConnectionKey, WatchdogRecord>()

    /** Most recent watchdog hit for a server. [uri] - the file being opened/read when it fired. */
    private data class WatchdogRecord(val lastAt: Long, val lastUri: String, val count: Int)

    companion object {
        // Window during which a fired watchdog blocks retries of the same stalled file.
        // Short enough that a quickly-recovered server does not punish the user for
        // a full minute; long enough that a truly-stalled server does not get four
        // 12-second open() hangs in a row.
        private const val WATCHDOG_WINDOW_MS = 15_000L

        // Within the window, this many watchdog hits for *different* files on the same
        // server escalates the lockout to server-wide - the server itself is stalled,
        // so further per-file honest attempts would just hang one after another.
        private const val SERVER_ESCALATION_THRESHOLD = 2
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

    /** Current state name for log context - avoids exposing enum across package boundary. */
    fun getStateName(key: ConnectionKey): String = states[key]?.name ?: "absent"

    /** Record a watchdog hit. [uri] identifies the file that stalled (media-item URI). */
    fun recordWatchdog(key: ConnectionKey, uri: String) {
        val now = System.currentTimeMillis()
        val prev = watchdogs[key]?.takeIf { now - it.lastAt < WATCHDOG_WINDOW_MS }
        val count = when {
            prev == null -> 1
            prev.lastUri != uri -> prev.count + 1
            else -> prev.count
        }
        watchdogs[key] = WatchdogRecord(lastAt = now, lastUri = uri, count = count)
        Timber.w("[SMB-PLAY] watchdog recorded for ${key.server} (state=${getStateName(key)}, count=$count)")
    }

    fun clearWatchdog(key: ConnectionKey) {
        watchdogs.remove(key)
    }

    /**
     * True if the caller must fail-fast instead of attempting [uri] on [key].
     * Fires for: a retry of the same stalled file within the window, OR - once the
     * server-escalation threshold is reached - any file on that server within the window.
     */
    fun isRecentWatchdog(key: ConnectionKey, uri: String): Boolean {
        val rec = watchdogs[key] ?: return false
        if (System.currentTimeMillis() - rec.lastAt >= WATCHDOG_WINDOW_MS) {
            watchdogs.remove(key)
            return false
        }
        return rec.count >= SERVER_ESCALATION_THRESHOLD || rec.lastUri == uri
    }

    /**
     * Drop all watchdog timestamps without touching connection states.
     * Called on user-initiated navigation: the user explicitly asked for a retry,
     * so the fail-fast lockout from a previous stall should not block them.
     */
    fun clearAllWatchdogs() {
        if (watchdogs.isNotEmpty()) {
            Timber.d("[SMB-PLAY] watchdogs cleared by user-initiated navigation")
        }
        watchdogs.clear()
    }

    /** Called on network reset or manual pool clear - allows fresh attempts after recovery. */
    fun clearAll() {
        states.clear()
        watchdogs.clear()
        Timber.d("[SMB-PLAY] tracker cleared (network reset or manual)")
    }
}
