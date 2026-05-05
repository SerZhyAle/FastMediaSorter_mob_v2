package com.sza.fastmediasorter.data.network.lifecycle

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton registry that maps a [NetworkProtocol] to its concrete [NetworkConnectionGate].
 * S0067.
 *
 * Per-protocol DI modules construct their gate and call [register]; the lifecycle observer
 * iterates [all] on `ProcessLifecycleOwner.ON_STOP`.
 */
@Singleton
class ConnectionGateRegistry @Inject constructor() {

    private val gates = ConcurrentHashMap<NetworkProtocol, NetworkConnectionGate<*>>()

    fun register(gate: NetworkConnectionGate<*>) {
        gates[gate.protocol] = gate
    }

    @Suppress("UNCHECKED_CAST")
    fun <C : Any> gateFor(protocol: NetworkProtocol): NetworkConnectionGate<C>? =
        gates[protocol] as NetworkConnectionGate<C>?

    fun all(): Collection<NetworkConnectionGate<*>> = gates.values
}
