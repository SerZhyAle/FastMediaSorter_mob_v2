package com.sza.fastmediasorter.data.repository

import android.os.SystemClock
import com.sza.fastmediasorter.data.networkmonitor.BluetoothSnapshotDataSource
import com.sza.fastmediasorter.data.networkmonitor.ConnectivitySample
import com.sza.fastmediasorter.data.networkmonitor.ConnectivitySnapshotDataSource
import com.sza.fastmediasorter.data.networkmonitor.TelephonySnapshotDataSource
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1433: composes one Monitor snapshot out of the three device readers.
 *
 * Connectivity is the only observed source; telephony and Bluetooth are read on each of its emissions. That
 * is deliberate and is what keeps the phase invariant "no background work" literally true - two more
 * observers would mean two more registrations to unregister, and neither of those two sources has an event
 * stream worth registering for. Connectivity already re-samples on a bounded tick while collected, so the
 * other two are no staler than it is.
 *
 * The rate cap lives here rather than in each observing view, per strategic §3.2's redraw budget: one
 * screen with several sections must cost one sample, not one per section.
 */
@Singleton
class NetworkMonitorRepositoryImpl @Inject constructor(
    private val connectivity: ConnectivitySnapshotDataSource,
    private val telephony: TelephonySnapshotDataSource,
    private val bluetooth: BluetoothSnapshotDataSource,
) : NetworkMonitorRepository {

    /**
     * Composition runs off the caller's thread on purpose.
     *
     * Both of the sampled sources reach the platform through binder - subscription and adapter lookups are
     * IPC, not field reads - and a visible section collects this once a second. Without [flowOn] that IPC
     * would land on whatever dispatcher the section collects on, which for a screen is the main thread, at
     * the exact rate strategic §3.2 caps the redraw cost at.
     */
    override fun observeSnapshot(): Flow<NetworkMonitorSnapshot> =
        connectivity.observe()
            .atMostOncePerInterval()
            .map { sample -> compose(sample) }
            .flowOn(Dispatchers.IO)

    private fun compose(sample: ConnectivitySample): NetworkMonitorSnapshot {
        val sims = telephony.sample()
        return NetworkMonitorSnapshot(
            networks = sample.networks,
            activeLink = sample.activeLink,
            wifi = sample.wifi,
            sims = sims.sims,
            activeModemCount = sims.activeModemCount,
            bluetooth = bluetooth.sample(),
            sampledAtMillis = System.currentTimeMillis(),
        )
    }

    /**
     * Drops emissions that arrive less than [MIN_EMIT_INTERVAL_MS] after the last one that got through.
     *
     * Drops rather than delays, and lets the first value straight out. A delaying operator would postpone
     * the very first paint by a second on a screen whose whole job is to show the current state, and a
     * dropped burst costs nothing here: the source re-samples on its own tick, so the newest state is never
     * more than that interval away.
     *
     * The cap is applied before composition so a suppressed emission costs no telephony or Bluetooth read
     * at all.
     */
    private fun <T> Flow<T>.atMostOncePerInterval(): Flow<T> = flow {
        var lastEmittedAt = 0L
        collect { value ->
            val now = SystemClock.elapsedRealtime()
            if (lastEmittedAt == 0L || now - lastEmittedAt >= MIN_EMIT_INTERVAL_MS) {
                lastEmittedAt = now
                emit(value)
            }
        }
    }

    private companion object {

        /**
         * Floor on the gap between two snapshots.
         *
         * Matches the source's own re-sample tick, so the two cannot fight: sampling faster upstream would
         * only produce emissions this cap throws away.
         */
        const val MIN_EMIT_INTERVAL_MS = 1000L
    }
}
