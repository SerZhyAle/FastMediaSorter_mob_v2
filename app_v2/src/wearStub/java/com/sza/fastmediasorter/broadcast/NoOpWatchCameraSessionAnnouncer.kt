package com.sza.fastmediasorter.broadcast

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3220: the inert announcer for a flavor with no Data Layer.
 *
 * Paired with `WearCameraSessionAnnouncer` in `src/wearGms/java/`; AGP mounts exactly one of the two
 * per flavor, and the contract is injected from `src/main`, so every flavor needs an implementation
 * whether or not a watch can exist.
 */
@Singleton
class NoOpWatchCameraSessionAnnouncer @Inject constructor() : WatchCameraSessionAnnouncer {

    override suspend fun announceSessionEnded() = Unit
}
