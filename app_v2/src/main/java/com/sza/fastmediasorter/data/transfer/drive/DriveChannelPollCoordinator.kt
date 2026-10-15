package com.sza.fastmediasorter.data.transfer.drive

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.util.JitteredBackoff
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DrivePollState
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.PollDemand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** S4231: whether the app is in the foreground; split out so the coordinator is testable without a process. */
fun interface ForegroundSignal {
    fun isForeground(): Flow<Boolean>
}

/** S4231: the process lifecycle - started means a screen of the app is visible. */
class ProcessForegroundSignal @Inject constructor() : ForegroundSignal {
    override fun isForeground(): Flow<Boolean> =
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
}

/**
 * S4231: the one poll of the Drive queue (contract DEVICE-EXCHANGE item CQ). One listing per interval for
 * the whole device: 10 seconds while any consumer holds an active demand, 60 seconds in the foreground
 * otherwise, nothing in the background (the opt-in worker covers that). Drive's rate limits and server
 * errors back off from 1 second to 5 minutes with jitter; a lost token stops the loop and deletes nothing;
 * turning the channel off cancels the loop at once.
 */
@Singleton
class DriveChannelPollCoordinator @Inject constructor(
    private val cycle: DriveChannelPollCycle,
    private val settings: DriveChannelSettings,
    private val foreground: ForegroundSignal,
    @param:ApplicationScope private val scope: CoroutineScope,
) : DriveQueueSnapshots {

    private val started = AtomicBoolean(false)
    private val nextDemandId = AtomicLong()
    private val demands = MutableStateFlow<Map<Long, PollDemand>>(emptyMap())
    private val latest = MutableSharedFlow<CrossDeviceQueueSnapshot>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val state = MutableStateFlow<DrivePollState>(DrivePollState.Off)

    internal var clock: () -> Long = System::currentTimeMillis

    override val snapshots: SharedFlow<CrossDeviceQueueSnapshot> = latest.asSharedFlow()

    override val pollState: StateFlow<DrivePollState> = state.asStateFlow()

    /** Begin following the channel switch, the foreground and the demands; idempotent. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(settings.enabled, foreground.isForeground(), demands) { enabled, visible, held ->
                intervalFor(enabled, visible, held.values)
            }
                .distinctUntilChanged()
                .collectLatest { interval ->
                    if (interval == null) state.value = DrivePollState.Off else poll(interval)
                }
        }
    }

    override fun acquire(demand: PollDemand): AutoCloseable {
        start()
        val id = nextDemandId.incrementAndGet()
        demands.update { it + (id to demand) }
        return AutoCloseable { demands.update { it - id } }
    }

    private suspend fun poll(intervalMs: Long) {
        Timber.d("S4231: Drive channel poll loop")
        val backoff = JitteredBackoff()
        var running = true
        while (running) {
            val result = cycle.run()
            val failure = result.exceptionOrNull()
            result.getOrNull()?.let { latest.emit(it) }
            val waitMs = when (failure) {
                null -> intervalMs.also { backoff.reset() }
                is DriveSignInRequired -> null
                // Listings go on; only uploads hit the daily limit, and nothing retries them in a loop.
                is DriveDailyLimit -> intervalMs
                else -> backoff.nextDelayMs()
            }
            state.value = stateFor(failure, intervalMs, waitMs)
            running = waitMs != null
            if (waitMs != null) delay(waitMs)
        }
    }

    private fun stateFor(failure: Throwable?, intervalMs: Long, waitMs: Long?): DrivePollState = when {
        failure == null -> DrivePollState.Polling(intervalMs)
        failure is DriveSignInRequired -> DrivePollState.SignInRequired
        failure is DriveDailyLimit -> DrivePollState.DailyLimit
        else -> DrivePollState.BackingOff(clock() + (waitMs ?: 0L))
    }

    companion object {
        val ACTIVE_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(ACTIVE_SECONDS)
        val FOREGROUND_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(FOREGROUND_SECONDS)

        /** The interval of item CQ for the current inputs, or null when this loop should not poll at all. */
        fun intervalFor(enabled: Boolean, foreground: Boolean, demands: Collection<PollDemand>): Long? = when {
            !enabled -> null
            PollDemand.ACTIVE in demands -> ACTIVE_INTERVAL_MS
            foreground || PollDemand.FOREGROUND in demands -> FOREGROUND_INTERVAL_MS
            else -> null
        }
    }
}

private const val ACTIVE_SECONDS = 10L
private const val FOREGROUND_SECONDS = 60L
