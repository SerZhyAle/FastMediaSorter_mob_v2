package com.sza.fastmediasorter.service

import com.sza.fastmediasorter.broadcast.WatchCameraSessionAnnouncer
import com.sza.fastmediasorter.broadcast.WatchCameraSessionRegistry
import com.sza.fastmediasorter.domain.model.WearCameraRefusal
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3220: forgets the camera session the phone was serving, and tells the watch it is over.
 *
 * The registry is cleared before the message is sent rather than after: the send can suspend for as
 * long as the Data Layer takes, and a stop command arriving from the watch in that window would read
 * a record whose broadcast has already gone and tear down whatever is running instead.
 *
 * `STOPPED` is the wire's only word for an ended session. It reads as "this watch asked", which is
 * not what happened here, but the wire enum is a contract an older watch decodes by name - a new
 * constant would reach that build as an unnameable refusal. S3223 carries the honest reason.
 */
@Singleton
class WearCameraSessionAnnouncer @Inject constructor(
    private val sessions: WatchCameraSessionRegistry,
    private val ackSender: CameraSessionAckSender
) : WatchCameraSessionAnnouncer {

    override suspend fun announceSessionEnded() {
        val session = sessions.current() ?: return
        Timber.d("S3220: broadcast ended, forgetting the watch camera session")
        sessions.clear()
        Timber.i("Telling the watch the camera session ended")
        ackSender.answerRefusal(session.nodeId, session.requestId, WearCameraRefusal.STOPPED)
    }
}
