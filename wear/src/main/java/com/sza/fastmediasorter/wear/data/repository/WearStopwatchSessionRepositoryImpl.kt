package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchClockReading
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchSnapshot
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3555: holds the measurement for the process and keeps a copy in the watch's settings store.
 *
 * Every update waits for the restore: a tap that lands while the process is still starting would otherwise
 * be applied to a clear stopwatch and then overwritten by the stored measurement it should have extended.
 */
@Singleton
class WearStopwatchSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: WearPreferencesRepository,
    @ApplicationScope scope: CoroutineScope
) : WearStopwatchSessionRepository {

    private val state = MutableStateFlow(WearStopwatchState.initial(WearStopwatchState.DEFAULT_COUNT))
    override val session: StateFlow<WearStopwatchState> = state.asStateFlow()

    private val restored = CompletableDeferred<Unit>()
    private val writes = Mutex()

    init {
        scope.launch {
            try {
                state.value = restore()
            } catch (e: IOException) {
                Timber.w(e, "Stopwatch session could not be read; starting from a clear stopwatch")
            } finally {
                restored.complete(Unit)
            }
        }
    }

    override suspend fun current(): WearStopwatchState {
        restored.await()
        return state.value
    }

    override suspend fun update(
        nowMillis: Long,
        transform: (WearStopwatchState, Long) -> WearStopwatchState
    ): Pair<WearStopwatchState, WearStopwatchState> {
        restored.await()
        return writes.withLock {
            val previous = state.value
            val next = transform(previous, nowMillis)
            state.value = next
            preferences.setStopwatchSession(WearStopwatchSnapshot.encode(next, reading()))
            previous to next
        }
    }

    private suspend fun restore(): WearStopwatchState {
        val stored = preferences.stopwatchSession.first()
        val decoded = WearStopwatchSnapshot.decode(stored, reading())
        if (stored != null && decoded == null) {
            Timber.w("Stored stopwatch session is unreadable; starting from a clear stopwatch")
        }
        return decoded ?: WearStopwatchState.initial(preferences.stopwatchParticipantCount.first())
    }

    private fun reading(): WearStopwatchClockReading = WearStopwatchClockReading(
        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        wallClockMillis = System.currentTimeMillis(),
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    )
}
