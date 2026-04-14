package com.sza.fastmediasorter.service

import android.content.SharedPreferences
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val PATH_REQUEST = "/fms/network_sources/request"
private const val PATH_ACK     = "/fms/network_sources/ack"
private const val PREFS_NAME   = "wear_sync_prefs"
private const val KEY_LAST_SYNC = "last_sync_timestamp"

@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {

    @Inject lateinit var sendResourcesToWatchUseCase: SendResourcesToWatchUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        Timber.d("PhoneWearListenerService: message received ${event.path}")
        when (event.path) {
            PATH_REQUEST -> handleSyncRequest()
            PATH_ACK     -> handleAck(event.data)
        }
    }

    private fun handleSyncRequest() {
        Timber.i("Watch requested sync — sending resources")
        serviceScope.launch {
            sendResourcesToWatchUseCase().onFailure { e ->
                Timber.e(e, "Failed to send resources on watch request")
            }
        }
    }

    private fun handleAck(data: ByteArray) {
        val json = data.decodeToString()
        Timber.i("Watch ack received: $json")
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        // Broadcast result to any active WearSyncViewModel via the companion object flow
        serviceScope.launch {
            WearSyncEvents.ackFlow.emit(json)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = PREFS_NAME
        const val LAST_SYNC = KEY_LAST_SYNC
    }
}

/** Process-wide event bus for ack messages from the watch. */
object WearSyncEvents {
    val ackFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
