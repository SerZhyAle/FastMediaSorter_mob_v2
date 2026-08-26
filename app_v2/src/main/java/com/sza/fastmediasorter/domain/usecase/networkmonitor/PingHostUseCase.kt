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

sealed interface PingHostState {
    data class Started(val host: String, val totalAttempts: Int) : PingHostState
    data class Attempt(val attemptIndex: Int, val totalAttempts: Int, val result: HostProbeResult) : PingHostState
    data class Finished(val host: String, val attempts: List<HostProbeResult>, val succeededCount: Int) : PingHostState
}

/**
 * S1617: runs a bounded sequence of ping probes against a target host and records the outcome.
 */
class PingHostUseCase @Inject constructor(
    private val hostProbe: HostProbe,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    operator fun invoke(
        host: String,
        networkLabel: String,
        attempts: Int = DEFAULT_ATTEMPTS,
        timeoutPerAttemptMs: Long = DEFAULT_TIMEOUT_PER_ATTEMPT_MS
    ): Flow<PingHostState> = channelFlow {
        val cleanHost = host.trim()
        val totalAttempts = attempts.coerceIn(1, MAX_ATTEMPTS)
        send(PingHostState.Started(cleanHost, totalAttempts))

        val results = mutableListOf<HostProbeResult>()
        var succeededCount = 0
        var totalLatencySum = 0.0

        try {
            for (i in 1..totalAttempts) {
                if (!isActive) break
                val result = hostProbe.probe(cleanHost, timeoutPerAttemptMs)
                results.add(result)
                if (result is HostProbeResult.Reached) {
                    succeededCount++
                    totalLatencySum += result.roundTripMillis
                }
                if (isActive) {
                    send(PingHostState.Attempt(i, totalAttempts, result))
                }
            }
            send(PingHostState.Finished(cleanHost, results, succeededCount))
        } finally {
            withContext(NonCancellable) {
                if (results.isNotEmpty()) {
                    val avgLatency = if (succeededCount > 0) (totalLatencySum / succeededCount).toLong() else null
                    historyRepository.record(
                        NetworkMeasurement(
                            takenAtMillis = System.currentTimeMillis(),
                            kind = NetworkMeasurementKind.PING,
                            networkLabel = networkLabel,
                            latencyMs = avgLatency,
                            resultText = "$cleanHost: $succeededCount/$totalAttempts replied",
                            succeeded = succeededCount > 0
                        )
                    )
                }
            }
        }
    }.flowOn(ioDispatcher)

    companion object {
        const val DEFAULT_ATTEMPTS = 4
        const val MAX_ATTEMPTS = 10
        const val DEFAULT_TIMEOUT_PER_ATTEMPT_MS = 2000L
    }
}
