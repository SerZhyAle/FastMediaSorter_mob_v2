package com.sza.fastmediasorter.data.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class IdleDisconnectPolicyImpl private constructor(
    private val timerScope: CoroutineScope,
) : IdleDisconnectPolicy {

    private data class TimerState(
        val generation: Long,
        val idleMs: Long,
        val callback: suspend () -> Unit,
    )

    private val timers = ConcurrentHashMap<String, Job>()
    private val states = ConcurrentHashMap<String, TimerState>()
    // Keep generations monotonic across disarm/re-arm cycles so stale callbacks
    // can never collide with a freshly armed transport lifecycle.
    private val generationSeeds = ConcurrentHashMap<String, Long>()

    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    internal constructor(dispatcher: CoroutineDispatcher) : this(
        CoroutineScope(SupervisorJob() + dispatcher),
    )

    override fun arm(transport: String, idleMs: Long, onTimeout: suspend () -> Unit) {
        val generation = nextGeneration(transport)
        states[transport] = TimerState(generation = generation, idleMs = idleMs, callback = onTimeout)
        Timber.i("IdleDisconnect: arm(transport=%s, idleMs=%d)", transport, idleMs)
        restartTimer(transport)
    }

    override fun touch(transport: String) {
        val entry = states[transport] ?: run {
            Timber.i("IdleDisconnect: touch ignored (transport=%s)", transport)
            return
        }
        val generation = nextGeneration(transport)
        states[transport] = entry.copy(generation = generation)
        Timber.i("IdleDisconnect: touch(transport=%s, idleMs=%d)", transport, entry.idleMs)
        restartTimer(transport)
    }

    override fun disarm(transport: String) {
        timers.remove(transport)?.cancel()
        val removed = states.remove(transport) != null
        Timber.i("IdleDisconnect: disarm(transport=%s, hadTimer=%s)", transport, removed)
    }

    private fun nextGeneration(transport: String): Long {
        var next = 0L
        generationSeeds.compute(transport) { _, current ->
            next = (current ?: 0L) + 1L
            next
        }
        return next
    }

    private fun restartTimer(transport: String) {
        val scheduledState = states[transport] ?: return
        timers.remove(transport)?.cancel()
        timers[transport] = timerScope.launch {
            delay(scheduledState.idleMs)
            val latestState = states[transport]
            if (latestState == null || latestState.generation != scheduledState.generation ||
                !states.remove(transport, latestState)
            ) {
                timers.remove(transport, coroutineContext[Job])
                Timber.d(
                    "IdleDisconnect: stale timeout dropped (transport=%s, generation=%d)",
                    transport,
                    scheduledState.generation,
                )
                return@launch
            }
            timers.remove(transport, coroutineContext[Job])
            Timber.i("IdleDisconnect: timeout fired (transport=%s)", transport)
            Timber.d("S0228: latest idle timeout accepted transport=$transport")
            runCatching { latestState.callback() }
                .onFailure {
                    Timber.e(it, "IdleDisconnect: timeout callback failed (transport=%s)", transport)
                }
        }
    }
}