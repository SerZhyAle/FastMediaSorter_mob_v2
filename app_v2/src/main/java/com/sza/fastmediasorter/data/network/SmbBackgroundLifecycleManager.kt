package com.sza.fastmediasorter.data.network

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0061 Phase 04: Observes the process-level lifecycle (registered in [FastMediaSorterApp]).
 *
 * When the app transitions from foreground to background ([onStop]) all SCANNER and PLAYER
 * pool entries are closed. This matches the pattern that SMBJ keeps TCP connections alive
 * until the OS reclaims them, but the server-side idle timer often fires before the user
 * returns - leading to a Broken-pipe on the next operation. Proactively closing them on
 * background removes stale entries from the pool so the next [withConnection] call always
 * starts fresh.
 *
 * BACKGROUND_WORKER connections are explicitly preserved so WorkManager transfers that
 * started while the app was in foreground can complete uninterrupted.
 *
 * Thread-safety: [SmbConnectionPool.closeAllExceptWorker] is thread-safe; this observer
 * itself runs on the main thread (lifecycle callbacks are posted to main), which is fine
 * because [closeUiConnections] immediately delegates to IO-dispatched async closes.
 */
@Singleton
class SmbBackgroundLifecycleManager @Inject constructor(
    private val smbConnectionManager: SmbConnectionManager
) : DefaultLifecycleObserver {

    override fun onStop(owner: LifecycleOwner) {
        Timber.i("App moved to background - releasing SMB UI connections")
        smbConnectionManager.closeUiConnections()
    }
}
