package com.sza.fastmediasorter.data.networkmonitor

import android.os.SystemClock
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.network.SmbErrorClassifier
import com.sza.fastmediasorter.data.network.TcpConnectOutcome
import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeUnavailability
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S1617: the named fallback for when the system `ping` cannot run at all.
 *
 * It calls the connect the app already uses in three places rather than opening a fourth socket of
 * its own (strategic ADR-3). What it cannot do is walk a path: a TCP connect reaches the endpoint or
 * nothing, so a caller asking for a single hop is told the measurement is impossible instead of
 * being handed the target's answer wearing a hop's label.
 */
class TcpConnectHostProbe @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HostProbe {

    override suspend fun probe(host: String, timeoutMillis: Long, ttl: Int?): HostProbeResult =
        when (ttl) {
            null -> withContext(ioDispatcher) { connectInOrder(host, timeoutMillis) }
            else -> HostProbeResult.NotMeasurable(
                HostProbeUnavailability.MECHANISM_UNAVAILABLE,
                "a TCP connect cannot address a single hop",
            )
        }

    /**
     * Two ports rather than one, because a single closed-and-filtered port would report a live host
     * as silent. The first decisive answer wins; a port that merely times out is not one.
     */
    private suspend fun connectInOrder(host: String, timeoutMillis: Long): HostProbeResult {
        val perPortBudget = (timeoutMillis / PROBE_PORTS.size).toInt().coerceAtLeast(MIN_PORT_BUDGET_MILLIS)
        val decisive = PROBE_PORTS.firstNotNullOfOrNull { port -> attempt(host, port, perPortBudget) }
        return decisive ?: HostProbeResult.NotReached
    }

    /** null means this port said nothing decisive, so the next one is worth trying. */
    private suspend fun attempt(host: String, port: Int, budgetMillis: Int): HostProbeResult? {
        // A blocking connect does not observe cancellation, so a cancelled traceroute would keep
        // paying for the remaining ports one budget at a time. Checked here rather than inside the
        // connect because that call belongs to the classifier the three other callers share.
        currentCoroutineContext().ensureActive()
        val startedAt = SystemClock.elapsedRealtime()
        return when (SmbErrorClassifier.classifyConnectivity(host, port, budgetMillis)) {
            TcpConnectOutcome.CONNECTED, TcpConnectOutcome.REFUSED ->
                HostProbeResult.Reached((SystemClock.elapsedRealtime() - startedAt).toDouble(), host)
            TcpConnectOutcome.NAME_NOT_RESOLVED ->
                HostProbeResult.NotMeasurable(HostProbeUnavailability.NAME_NOT_RESOLVED, null)
            TcpConnectOutcome.NO_NETWORK ->
                HostProbeResult.NotMeasurable(HostProbeUnavailability.NO_NETWORK, null)
            TcpConnectOutcome.TIMED_OUT, TcpConnectOutcome.FAILED -> null
        }
    }

    private companion object {
        val PROBE_PORTS = listOf(443, 80)
        const val MIN_PORT_BUDGET_MILLIS = 400
    }
}
