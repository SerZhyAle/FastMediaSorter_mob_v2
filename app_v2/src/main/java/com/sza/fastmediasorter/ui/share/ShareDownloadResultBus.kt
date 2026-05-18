package com.sza.fastmediasorter.ui.share

import com.sza.fastmediasorter.domain.usecase.link.LinkAutoDownloadCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0202 - application-scoped bus that lets [com.sza.fastmediasorter.worker.LinkDownloadWorker]
 * push a terminal share-download result to whichever Activity is foreground at that moment
 * (typically [com.sza.fastmediasorter.ui.main.MainActivity] when the user returns to the app
 * from the source app like Instagram/Threads).
 *
 * `notificationShown = true` indicates the worker has already published a result notification
 * in the system tray; the consumer should suppress redundant toasts in that case to avoid
 * double-feedback. Consumers may still react to specific result kinds (e.g. open the player
 * for a successful save when `linkAutoDownloadOpenInPlayer` is enabled).
 *
 * `replay = 1` means a single most-recent terminal result is retained - Activity recreation
 * (rotation) consumes the same emission once; clearing the cache is the consumer's job after
 * acting on the value, so a stale outcome is not re-shown after navigation.
 */
@Singleton
class ShareDownloadResultBus @Inject constructor() {

    data class Pending(
        val url: String,
        val result: LinkAutoDownloadCoordinator.Result,
        val notificationShown: Boolean,
        val emittedAt: Long = System.currentTimeMillis(),
    )

    private val _pending = MutableSharedFlow<Pending>(replay = 1, extraBufferCapacity = 4)
    val pending: SharedFlow<Pending> = _pending.asSharedFlow()

    suspend fun publish(p: Pending) {
        _pending.emit(p)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearReplayCache() {
        _pending.resetReplayCache()
    }
}
