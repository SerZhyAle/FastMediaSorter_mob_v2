package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.networkmonitor.HostProbe
import com.sza.fastmediasorter.domain.networkmonitor.HostProbeResult
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface TraceRouteState {
    data class Started(val host: String, val maxHops: Int) : TraceRouteState
    data class Hop(val hopIndex: Int, val result: HostProbeResult) : TraceRouteState
    data class Finished(val host: String, val hops: List<HostProbeResult>, val reachedTarget: Boolean) : TraceRouteState
}

/**
 * S1617: walks the network path to a target host hop by hop using incremental TTL probes.
 */
class TraceRouteUseCase @Inject constructor(
    private val hostProbe: HostProbe,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(
        host: String,
        networkLabel: String,
        maxHops: Int = DEFAULT_MAX_HOPS,
        timeoutPerHopMs: Long = DEFAULT_TIMEOUT_PER_HOP_MS
    ): Flow<TraceRouteState> = channelFlow {
        val cleanHost = host.trim()
        val capHops = maxHops.coerceIn(1, MAX_ALLOWED_HOPS)
        send(TraceRouteState.Started(cleanHost, capHops))

        val hops = mutableListOf<HostProbeResult>()
        var reachedTarget = false

        try {
            for (ttl in 1..capHops) {
                if (!isActive) break
                val result = hostProbe.probe(cleanHost, timeoutPerHopMs, ttl = ttl)
                hops.add(result)

                if (isActive) {
                    send(TraceRouteState.Hop(ttl, result))
                }

                when (result) {
                    is HostProbeResult.Reached -> {
                        reachedTarget = true
                        break
                    }
                    is HostProbeResult.NotMeasurable -> {
                        // Mechanism is unavailable or unresolvable name - no point in continuing ladder
                        if (ttl == 1) {
                            break
                        }
                    }
                    else -> {
                        // HopAnswered or NotReached (silent hop) -> continue ladder
                    }
                }
            }
            send(TraceRouteState.Finished(cleanHost, hops, reachedTarget))
        } finally {
            withContext(NonCancellable) {
                if (hops.isNotEmpty()) {
                    val summaryText = if (reachedTarget) {
                        "$cleanHost reached in ${hops.size} hop(s)"
                    } else {
                        "$cleanHost path traced (${hops.size} hop(s))"
                    }
                    historyRepository.record(
                        NetworkMeasurement(
                            takenAtMillis = System.currentTimeMillis(),
                            kind = NetworkMeasurementKind.TRACEROUTE,
                            networkLabel = networkLabel,
                            resultText = summaryText,
                            succeeded = reachedTarget
                        )
                    )
                }
            }
        }
    }.flowOn(ioDispatcher)

    companion object {
        const val DEFAULT_MAX_HOPS = 30
        const val MAX_ALLOWED_HOPS = 30
        const val DEFAULT_TIMEOUT_PER_HOP_MS = 2000L
    }
}
