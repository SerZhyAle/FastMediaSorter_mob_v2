package com.sza.fastmediasorter.core.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.network.HotspotState
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcast-backed implementation of [HotspotStateSource] using the protected system broadcast
 * "android.net.conn.TETHER_STATE_CHANGED" and evaluating soft-AP interface state via [SoftApInterfaceProbe].
 */
@Singleton
class TetherBroadcastHotspotStateSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val probe: SoftApInterfaceProbe
) : HotspotStateSource {

    override fun state(): Flow<HotspotState> = callbackFlow {
        // Emit initial probe verdict
        trySend(probe.probe())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(probe.probe())
            }
        }

        // TETHER_STATE_CHANGED is a hidden protected system broadcast constant without public SDK definition.
        val actionTetherStateChanged = "android.net.conn.TETHER_STATE_CHANGED"
        val filter = IntentFilter(actionTetherStateChanged)

        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to register TETHER_STATE_CHANGED receiver, using single probe read fallback")
        }.isSuccess

        awaitClose {
            if (registered) {
                runCatching {
                    context.unregisterReceiver(receiver)
                }.onFailure { throwable ->
                    Timber.w(throwable, "Failed to unregister TETHER_STATE_CHANGED receiver")
                }
            }
        }
    }.distinctUntilChanged()
}
