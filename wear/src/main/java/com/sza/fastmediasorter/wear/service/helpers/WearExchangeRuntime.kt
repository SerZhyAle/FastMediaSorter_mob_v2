package com.sza.fastmediasorter.wear.service.helpers

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeControlStreams
import com.sza.fastmediasorter.wear.data.exchange.WearExchangePresence
import com.sza.fastmediasorter.wear.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the watch's exchange layer with the process: the control streams under the presence policy, the
 * broadcast announcer and the cast coordinator. Each outlives every screen, so a broadcast keeps being announced and
 * a cast keeps being heard after the window that started them is gone.
 */
@Singleton
class WearExchangeRuntime @Inject constructor(
    private val streams: WearExchangeControlStreams,
    private val presence: WearExchangePresence,
    private val announcer: WearExchangeBroadcastAnnouncer,
    private val coordinator: WearExchangeCastCoordinator,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { streams.run(presence.wanted(coordinator.playing)) }
        scope.launch { announcer.follow() }
        scope.launch { coordinator.follow() }
    }
}
