package com.sza.fastmediasorter.data.tourist

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterRecord
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import com.sza.fastmediasorter.domain.repository.TouristCounterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4184: the counter in a small preferences file, like the trip next door (S4145) - a few scalars and a
 * capped history list need no schema, and a malformed history entry is dropped alone.
 *
 * The file is read once, on first use and off the main thread; afterwards the in-memory state is the
 * truth and every update writes it back.
 */
@Singleton
class TouristCounterRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TouristCounterRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val mutex = Mutex()
    private val current = MutableStateFlow(TouristCounterState())

    @Volatile
    private var loaded = false

    override fun observe(): Flow<TouristCounterState> = flow {
        ensureLoaded()
        emitAll(current)
    }

    override suspend fun update(transform: (TouristCounterState) -> TouristCounterState) {
        ensureLoaded()
        mutex.withLock {
            val next = transform(current.value)
            if (next == current.value) return
            current.value = next
            withContext(Dispatchers.IO) { save(next) }
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            current.value = withContext(Dispatchers.IO) { load() }
            loaded = true
        }
    }

    private fun load(): TouristCounterState = TouristCounterState(
        value = prefs.getLong(KEY_VALUE, 0L),
        startValue = prefs.getLong(KEY_START_VALUE, 0L),
        sessionStartedAtMillis = prefs.getLong(KEY_SESSION_STARTED, NO_LONG).takeIf { it != NO_LONG },
        extraStepsVisible = prefs.getBoolean(KEY_EXTRA_STEPS, false),
        history = decodeHistory(prefs.getString(KEY_HISTORY, null)),
    )

    private fun save(state: TouristCounterState) {
        prefs.edit()
            .putLong(KEY_VALUE, state.value)
            .putLong(KEY_START_VALUE, state.startValue)
            .putLong(KEY_SESSION_STARTED, state.sessionStartedAtMillis ?: NO_LONG)
            .putBoolean(KEY_EXTRA_STEPS, state.extraStepsVisible)
            .putString(KEY_HISTORY, encodeHistory(state.history))
            .apply()
    }

    private fun encodeHistory(records: List<TouristCounterRecord>): String =
        records.joinToString(RECORD_SEPARATOR) {
            "${it.value}$FIELD_SEPARATOR${it.startedAtMillis}$FIELD_SEPARATOR${it.endedAtMillis}"
        }

    private fun decodeHistory(raw: String?): List<TouristCounterRecord> =
        raw.orEmpty().split(RECORD_SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEPARATOR)
            val value = parts.getOrNull(0)?.toLongOrNull()
            val started = parts.getOrNull(1)?.toLongOrNull()
            val ended = parts.getOrNull(2)?.toLongOrNull()
            if (value != null && started != null && ended != null) {
                TouristCounterRecord(value, started, ended)
            } else {
                null
            }
        }

    private companion object {
        const val PREFS_NAME = "tourist_counter"
        const val NO_LONG = Long.MIN_VALUE
        const val RECORD_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
        const val KEY_VALUE = "value"
        const val KEY_START_VALUE = "start_value"
        const val KEY_SESSION_STARTED = "session_started_at"
        const val KEY_EXTRA_STEPS = "extra_steps_visible"
        const val KEY_HISTORY = "history"
    }
}
