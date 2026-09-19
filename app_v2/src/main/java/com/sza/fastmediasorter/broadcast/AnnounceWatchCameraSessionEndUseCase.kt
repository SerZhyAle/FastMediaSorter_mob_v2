package com.sza.fastmediasorter.broadcast

import javax.inject.Inject

/**
 * S3220: watches the broadcast state and announces every exit from [BroadcastState.Live].
 *
 * The state rather than [BroadcastSourceController.stop] is the source, because a stop the owner
 * pressed is only one of the ways a session ends: a capture that failed and a service the system took
 * down both leave `Live` without anything calling `stop()`, and those are exactly the endings nobody
 * was naming.
 *
 * Lives in `src/main` beside the session registry rather than in `wearGms` with the sender: that
 * source set is mounted by directory into the shipping variants only and into no unit-test variant at
 * all, so the transition rule written there could not be judged without a phone and a watch in hand.
 */
class AnnounceWatchCameraSessionEndUseCase @Inject constructor(
    private val controller: BroadcastSourceController,
    private val announcer: WatchCameraSessionAnnouncer
) {

    /**
     * Collects until the calling scope is cancelled, which is the life of the process.
     *
     * A build with the broadcast layer disabled returns instead of collecting: its state never leaves
     * `Idle`, so the collector would hold a subscription for a transition that cannot happen.
     */
    suspend fun observe() {
        if (!controller.isAvailable) return
        var previous = controller.state.value
        controller.state.collect { current ->
            if (previous is BroadcastState.Live && current !is BroadcastState.Live) {
                announcer.announceSessionEnded()
            }
            previous = current
        }
    }
}
