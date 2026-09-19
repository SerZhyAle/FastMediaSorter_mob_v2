package com.sza.fastmediasorter.broadcast

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The camera session a watch is currently being served, as the phone remembers it.
 *
 * [startedByWatch] is what tells a session this watch asked for from one that was already running
 * when it asked.
 */
data class WatchCameraSession(
    val nodeId: String,
    val requestId: String,
    val url: String,
    val lenses: BroadcastCameraLenses,
    val startedByWatch: Boolean
)

/**
 * S3117: holds the one camera session this phone is serving to a watch.
 *
 * Needed because the three commands arrive as separate Data Layer messages with nothing linking them:
 * a stop or a lens switch carries a request id and no address, so without a record of what was served
 * the phone could neither re-answer the switch with the same URL nor tell whether the broadcast it is
 * about to stop is one the watch started.
 *
 * One session rather than a map: the wire refuses a second request with `BUSY`, so a second entry
 * could only ever be a stale one.
 */
@Singleton
class WatchCameraSessionRegistry @Inject constructor() {

    // Written from the Data Layer listener's scope and read from it; volatile because the listener's
    // callbacks and the application scope are not guaranteed to be the same thread.
    @Volatile
    private var session: WatchCameraSession? = null

    fun remember(session: WatchCameraSession) {
        this.session = session
    }

    fun current(): WatchCameraSession? = session

    fun clear() {
        session = null
    }
}
