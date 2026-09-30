package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.model.PhoneCompanionState
import com.sza.fastmediasorter.wear.domain.model.resolvePhoneCompanionState
import com.sza.fastmediasorter.wear.domain.repository.PhoneCompanionRepository
import com.sza.fastmediasorter.wear.util.warnUnlessCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4011: asks the Data Layer whether a connected phone carries FastMediaSorter.
 *
 * Lookups run on the process-lifetime scope, never on a caller's: the listener service that also
 * triggers one is destroyed about 1.5 s after its callback, and a cancelled lookup reports nothing.
 * Requests go through a conflated channel so a burst of capability callbacks costs one lookup, and a
 * request sent before the collector starts is still delivered.
 */
@Singleton
class PhoneCompanionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : PhoneCompanionRepository {

    private val _state = MutableStateFlow(PhoneCompanionState.UNKNOWN)
    override val state: StateFlow<PhoneCompanionState> = _state.asStateFlow()

    private val requests = Channel<Unit>(Channel.CONFLATED)

    /**
     * The capability disappears both when the app is removed and when the phone walks away, and only
     * a fresh node lookup can tell those two apart - so a change re-runs the whole lookup rather than
     * trusting the node set the callback was handed.
     */
    private val capabilityChanges: Flow<Unit> = callbackFlow {
        val client = Wearable.getCapabilityClient(context)
        val listener = CapabilityClient.OnCapabilityChangedListener { trySend(Unit) }
        client.addListener(listener, PHONE_COMPANION_CAPABILITY)
        awaitClose { client.removeListener(listener, PHONE_COMPANION_CAPABILITY) }
    }

    init {
        // Listens only while a screen observes the state: the registration ends with the last
        // subscriber rather than living on for a process that may be serving a tile or a service.
        scope.launch {
            _state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { observed ->
                    if (observed) capabilityChanges.collect { requests.trySend(Unit) }
                }
        }
        scope.launch {
            requests.receiveAsFlow().collectLatest {
                val resolved = lookup()
                if (_state.value != resolved) {
                    Timber.i("Phone companion state: %s -> %s", _state.value, resolved)
                }
                _state.value = resolved
            }
        }
    }

    override fun refresh() {
        requests.trySend(Unit)
    }

    private suspend fun lookup(): PhoneCompanionState = runCatching {
        val connected = Wearable.getNodeClient(context).connectedNodes.await()
            .mapTo(mutableSetOf()) { it.id }
        val capable = Wearable.getCapabilityClient(context)
            .getCapability(PHONE_COMPANION_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
            .mapTo(mutableSetOf()) { it.id }
        resolvePhoneCompanionState(connected, capable)
    }.onFailure { it.warnUnlessCancellation("Phone companion lookup failed") }
        .getOrDefault(PhoneCompanionState.PHONE_UNREACHABLE)

    companion object {
        /**
         * S1862: the capability the phone companion advertises. Repeated as a literal in the
         * CAPABILITY_CHANGED filter of `wear/src/main/AndroidManifest.xml`, because a manifest cannot
         * reference a Kotlin constant, and it must equal the name the phone declares in its own
         * `res/values/wear.xml`.
         */
        const val PHONE_COMPANION_CAPABILITY = "fms_phone_companion"
    }
}
