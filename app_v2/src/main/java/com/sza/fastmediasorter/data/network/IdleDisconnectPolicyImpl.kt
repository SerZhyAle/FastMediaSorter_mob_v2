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

    private data class TimerEntry(
        val idleMs: Long,
        val callback: suspend () -> Unit,
    )

    private val timers = ConcurrentHashMap<String, Job>()
    private val entries = ConcurrentHashMap<String, TimerEntry>()

    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default))

    internal constructor(dispatcher: CoroutineDispatcher) : this(
        CoroutineScope(SupervisorJob() + dispatcher),
    )

    override fun arm(transport: String, idleMs: Long, onTimeout: suspend () -> Unit) {
        entries[transport] = TimerEntry(idleMs = idleMs, callback = onTimeout)
        Timber.i("IdleDisconnect: arm(transport=%s, idleMs=%d)", transport, idleMs)
        restartTimer(transport)
    }

    override fun touch(transport: String) {
        val entry = entries[transport] ?: run {
            Timber.i("IdleDisconnect: touch ignored (transport=%s)", transport)
            return
        }
        Timber.i("IdleDisconnect: touch(transport=%s, idleMs=%d)", transport, entry.idleMs)
        restartTimer(transport)
    }

    override fun disarm(transport: String) {
        timers.remove(transport)?.cancel()
        val removed = entries.remove(transport) != null
        Timber.i("IdleDisconnect: disarm(transport=%s, hadTimer=%s)", transport, removed)
    }

    private fun restartTimer(transport: String) {
        val entry = entries[transport] ?: return
        timers.remove(transport)?.cancel()
        timers[transport] = timerScope.launch {
            delay(entry.idleMs)
            timers.remove(transport, coroutineContext[Job])
            entries.remove(transport)
            Timber.i("IdleDisconnect: timeout fired (transport=%s)", transport)
            runCatching { entry.callback() }
                .onFailure {
                    Timber.e(it, "IdleDisconnect: timeout callback failed (transport=%s)", transport)
                }
        }
    }
}