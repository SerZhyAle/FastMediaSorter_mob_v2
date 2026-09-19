package com.sza.fastmediasorter.broadcast

/**
 * S3220: how this phone names the end of a camera session it was serving to a watch.
 *
 * The phone used to say nothing when the owner stopped the broadcast himself: the only `STOPPED`
 * answer left `PhoneCameraSessionCommandManager.handleStop`, which runs for a stop the watch asked
 * for. Everything else - the owner's own stop, a capture that died - left the watch to conclude the
 * end from a refused socket and left the session record behind.
 *
 * An implementation owes both halves: forget the session, then tell the node that was being served.
 * Forgetting matters on its own, because a record outliving its broadcast makes a later stop from the
 * watch tear down a broadcast the watch never started.
 */
interface WatchCameraSessionAnnouncer {

    suspend fun announceSessionEnded()
}
